# Keyboard

Telos Keyboard is an Android input method inside the Telos APK. Opening its settings does not itself make it the active keyboard.

1. Open Keyboard in Telos and tap **Open input settings**.
2. Enable **Telos Keyboard** in Android’s on-screen keyboard list.
3. Return to Telos and tap **Choose keyboard**, then select Telos Keyboard in Android’s chooser.
4. Focus a normal text field to open it. The language key switches Greek and English; numeric editors open the number layout.

The keyboard supports direct key taps, symbols, emoji and standard Search/Done/Send editor actions. The full-screen gesture overlay has been removed from the typing path; its old fixed-word heuristic is not swipe recognition.

Password/PIN and no-learning editors enforce private input independently of the manual incognito preference. Transient word and clipboard state clears when editors change. Such private input does not populate the suggestion buffer.

The IME keeps its own opaque input surface while using the launcher’s colors and typography. It displays an immediate Material fallback while theme preferences load and does not load wallpaper blur for the input window.

Validation on the affected OnePlus/Android 17 device remains outstanding.
