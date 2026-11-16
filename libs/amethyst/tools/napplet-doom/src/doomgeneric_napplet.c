// doomgeneric platform layer for a NIP-5D napplet.
//
// Everything that touches the outside world goes through the `doom` import module that
// index.html supplies: the frame buffer, the clock, sound effects and music. The engine never
// blocks: the page calls dg_tick() once per new game tic, and DG_SleepMs() is a no-op because a
// napplet runs on the page's main thread. File I/O (the WAD, the config, save games) goes through
// WASI against the page's in-memory file system.
//
// SPDX-License-Identifier: GPL-2.0-or-later
// It implements doomgeneric's platform interface and adapts its SDL sound module's lump naming,
// so it carries doomgeneric's license; the built index.html is GPL too (see README.md).

#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "doomgeneric.h"
#include "doomtype.h"
#include "i_sound.h"
#include "memio.h"
#include "mus2mid.h"
#include "w_wad.h"
#include "z_zone.h"
#include "deh_str.h"
#include "m_misc.h"

#define IMPORT(name) __attribute__((import_module("doom"), import_name(name)))
#define EXPORT(name) __attribute__((export_name(name)))

IMPORT("draw_frame") void js_draw_frame(const pixel_t *pixels, int width, int height);
IMPORT("ticks_ms") uint32_t js_ticks_ms(void);
IMPORT("sfx_start") void js_sfx_start(int channel, int lump, const uint8_t *data, int length, int vol, int sep);
IMPORT("sfx_update") void js_sfx_update(int channel, int vol, int sep);
IMPORT("sfx_stop") void js_sfx_stop(int channel);
IMPORT("sfx_playing") int js_sfx_playing(int channel);
IMPORT("music_play") void js_music_play(const uint8_t *midi, int length, int looping);
IMPORT("music_stop") void js_music_stop(void);
IMPORT("music_volume") void js_music_volume(int volume);
IMPORT("music_pause") void js_music_pause(int paused);

// ---- Lifecycle -------------------------------------------------------------------------------

void D_WipeStep(void);
boolean D_WipeInProgress(void);

// The page writes the IWAD's file name here before dg_start(); doomgeneric tells shareware,
// registered and Freedoom apart partly by that name.
static char iwad_name[64] = "freedoom1.wad";

// Mods: the page mounts each PWAD in the file system and writes its name into a slot here; an
// empty slot ends the list. They load in order after the IWAD, as `-file` does.
#define MAX_PWADS 8
static char pwad_names[MAX_PWADS][64];
static char *dg_argv[4 + MAX_PWADS];

EXPORT("dg_iwad_name") char *dg_iwad_name(void)
{
    return iwad_name;
}

EXPORT("dg_pwad_name") char *dg_pwad_name(int slot)
{
    return slot >= 0 && slot < MAX_PWADS ? pwad_names[slot] : NULL;
}

static int build_argv(void)
{
    int argc = 0;
    dg_argv[argc++] = "doom";
    dg_argv[argc++] = "-iwad";
    dg_argv[argc++] = iwad_name;
    if (pwad_names[0][0] != '\0')
    {
        dg_argv[argc++] = "-file";
        for (int i = 0; i < MAX_PWADS && pwad_names[i][0] != '\0'; i++)
            dg_argv[argc++] = pwad_names[i];
    }
    dg_argv[argc] = NULL;
    return argc;
}

extern int key_prevweapon;
extern int key_nextweapon;

EXPORT("dg_start") void dg_start(void)
{
    // Vanilla leaves weapon cycling unbound; the touch controls need it. A saved config wins.
    key_prevweapon = '[';
    key_nextweapon = ']';
    doomgeneric_Create(build_argv(), dg_argv);
}

EXPORT("dg_tick") void dg_tick(void)
{
    // The screen melt used to block inside D_Display until it finished; it now advances one
    // step per call, and the game waits for it the way it did on DOS.
    if (D_WipeInProgress())
    {
        D_WipeStep();
        return;
    }
    doomgeneric_Tick();
}

// ---- Video, clock, input ---------------------------------------------------------------------

void DG_Init(void) {}

void DG_DrawFrame(void)
{
    js_draw_frame(DG_ScreenBuffer, DOOMGENERIC_RESX, DOOMGENERIC_RESY);
}

void DG_SleepMs(uint32_t ms) { (void) ms; }

uint32_t DG_GetTicksMs(void)
{
    return js_ticks_ms();
}

void DG_SetWindowTitle(const char *title) { (void) title; }

#define KEY_QUEUE_SIZE 64

static uint16_t key_queue[KEY_QUEUE_SIZE];
static unsigned key_write = 0;
static unsigned key_read = 0;

EXPORT("dg_key") void dg_key(int pressed, int doom_key)
{
    unsigned next = (key_write + 1) % KEY_QUEUE_SIZE;
    if (next == key_read) return; // full: drop rather than overwrite unread events
    key_queue[key_write] = (uint16_t) ((pressed ? 1 : 0) << 8 | (doom_key & 0xff));
    key_write = next;
}

int DG_GetKey(int *pressed, unsigned char *key)
{
    if (key_read == key_write) return 0;
    uint16_t data = key_queue[key_read];
    key_read = (key_read + 1) % KEY_QUEUE_SIZE;
    *pressed = data >> 8;
    *key = data & 0xff;
    return 1;
}

// i_system.c shells out to zenity for error boxes; WASI has no processes.
int system(const char *command)
{
    (void) command;
    return -1;
}

// ---- Sound effects: raw DMX lumps, decoded and mixed by Web Audio -----------------------------

// Config variables i_sound.c binds; they belong to the SDL module this file replaces.
int use_libsamplerate = 0;
float libsamplerate_scale = 0.65f;

static boolean use_sfx_prefix;
static snddevice_t sound_devices[] = {SNDDEVICE_SB, SNDDEVICE_PAS, SNDDEVICE_GUS,
                                      SNDDEVICE_WAVEBLASTER, SNDDEVICE_SOUNDCANVAS, SNDDEVICE_AWE32};

static boolean Sfx_Init(boolean prefix)
{
    use_sfx_prefix = prefix;
    return true;
}

static void Sfx_Shutdown(void) {}

static int Sfx_GetLumpNum(sfxinfo_t *sfx)
{
    char name[9];
    if (sfx->link != NULL) sfx = sfx->link;
    if (use_sfx_prefix)
        M_snprintf(name, sizeof(name), "ds%s", DEH_String(sfx->name));
    else
        M_StringCopy(name, DEH_String(sfx->name), sizeof(name));
    return W_GetNumForName(name);
}

static void Sfx_Update(void) {}

static void Sfx_UpdateParams(int channel, int vol, int sep)
{
    js_sfx_update(channel, vol, sep);
}

static int Sfx_Start(sfxinfo_t *sfx, int channel, int vol, int sep)
{
    if (sfx->lumpnum < 0) return -1;
    // PU_STATIC: the bytes stay put, so the page can cache the decoded buffer by lump number.
    const uint8_t *data = W_CacheLumpNum(sfx->lumpnum, PU_STATIC);
    js_sfx_start(channel, sfx->lumpnum, data, W_LumpLength(sfx->lumpnum), vol, sep);
    return channel;
}

static void Sfx_Stop(int channel)
{
    js_sfx_stop(channel);
}

static boolean Sfx_IsPlaying(int channel)
{
    return js_sfx_playing(channel) != 0;
}

static void Sfx_Precache(sfxinfo_t *sounds, int num_sounds)
{
    (void) sounds;
    (void) num_sounds;
}

sound_module_t DG_sound_module = {
    sound_devices,
    arrlen(sound_devices),
    Sfx_Init,
    Sfx_Shutdown,
    Sfx_GetLumpNum,
    Sfx_Update,
    Sfx_UpdateParams,
    Sfx_Start,
    Sfx_Stop,
    Sfx_IsPlaying,
    Sfx_Precache,
};

// ---- Music: MUS lumps become MIDI here, the page synthesizes them ----------------------------

typedef struct
{
    void *midi;
    size_t length;
} song_t;

static boolean music_playing = false;

static boolean Music_Init(void)
{
    return true;
}

static void Music_Shutdown(void)
{
    js_music_stop();
}

static void Music_SetVolume(int volume)
{
    js_music_volume(volume);
}

static void Music_Pause(void)
{
    js_music_pause(1);
}

static void Music_Resume(void)
{
    js_music_pause(0);
}

static void *Music_Register(void *data, int length)
{
    song_t *song = malloc(sizeof(song_t));
    if (song == NULL) return NULL;

    if (length >= 4 && memcmp(data, "MThd", 4) == 0)
    {
        song->midi = malloc(length);
        memcpy(song->midi, data, length);
        song->length = length;
        return song;
    }

    MEMFILE *in = mem_fopen_read(data, length);
    MEMFILE *out = mem_fopen_write();
    void *buf;
    size_t buflen;
    // mus2mid returns true on failure.
    if (mus2mid(in, out))
    {
        mem_fclose(in);
        mem_fclose(out);
        free(song);
        return NULL;
    }
    mem_get_buf(out, &buf, &buflen);
    song->midi = malloc(buflen);
    memcpy(song->midi, buf, buflen);
    song->length = buflen;
    mem_fclose(in);
    mem_fclose(out);
    return song;
}

static void Music_Unregister(void *handle)
{
    song_t *song = handle;
    if (song == NULL) return;
    free(song->midi);
    free(song);
}

static void Music_Play(void *handle, boolean looping)
{
    song_t *song = handle;
    if (song == NULL) return;
    js_music_play(song->midi, (int) song->length, looping);
    music_playing = true;
}

static void Music_Stop(void)
{
    js_music_stop();
    music_playing = false;
}

static boolean Music_IsPlaying(void)
{
    return music_playing;
}

static void Music_Poll(void) {}

music_module_t DG_music_module = {
    sound_devices,
    arrlen(sound_devices),
    Music_Init,
    Music_Shutdown,
    Music_SetVolume,
    Music_Pause,
    Music_Resume,
    Music_Register,
    Music_Unregister,
    Music_Play,
    Music_Stop,
    Music_IsPlaying,
    Music_Poll,
};
