# Pulse 2.0 — návrh a předání implementace

Stav: **pouze návrh, čeká na potvrzení uživatele a přepnutí modelu**. 2026-10-15.
Přímá instrukce v chatu má přednost před přiloženým promptem požadujícím okamžitou implementaci. Nespouštět implementaci, instalaci ani testy ovládající telefon před potvrzením. Rozpracování task 050 je odloženo; žádné jeho změny kódu nevznikly.

## Ověřený výchozí bod

Lokální základ `09bf44885`, původní větev `codex/intelia-wallpaper-design`, čistý strom před tímto návrhem. Samostatná větev dokumentace `codex/pulse-v2-design` vznikla z tohoto commitu; je současně návratovým bodem pro zdrojový kód. Není zálohou uživatelských dat. Před implementací znovu ověřit stav a vytvořit checkpoint případných novějších změn. Existující draft PR #3 patří původní větvi; jeho vzdálený stav nebyl v tomto dokumentačním úseku znovu ověřen. Nic nepushovat ani neslučovat bez oprávnění.

Současný `InteliaIslandService` je NotificationListenerService s opt-in TYPE_APPLICATION_OVERLAY, MediaSession callbacky, jednoduchým compact/expanded panelem, aplikacemi ARC a explicitním FLOW preview. Nečte payloady oznámení. Pozorování závisí na připojeném listeneru, overlay oprávnění, odemčení a enabled/hidden stavu. Má jedinou vybranou mediální session; vlastní plnohodnotný event/gesture/action engine ani ovládání cutoutu dosud nemá. `InteliaIslandPolicy` je jednoduchá podmínka viditelnosti, nikoliv nový stavový automat.

Znovu použít `FlowAliasRuntime`, `SearchActionRepository`, profilově bezpečné dispatch adaptéry, `ArcGesture` principy výběru a cancellation, `InteliaIconEngine`, Koin, coroutine lifecycle a `InteliaUiPreferences`/`.intelia`. Dnešní UI codec přijímá pouze version=1; migrace vyžaduje vědomou změnu, nelze pouze zvýšit číslo. Předchozí výsledky testů a telefonu jsou v NATIVE_FOUNDATION_REPORT; nejsou výsledky Pulse 2.0. Telefon byl dostupný před změnou zadání, v této dokumentační fázi nebyl ovládán.

## Rozhodující ověření proveditelnosti

Standardní application overlay je pod status barem a IME. DisplayCutout poskytuje geometrii, ne právo zachytávat dotyk u kamery. Nelze slíbit souběžně neviditelný aktivní hitbox a průchod všech stejných dotyků do systémové lišty. Po převzetí DOWN nelze spoléhat, že nevyužitý swipe dodatečně obdrží původní systémový příjemce. Žádné přeposílání syntetických dotyků.

Po potvrzení implementace nejdříve samostatný experiment na Nothing: WindowInsets/DisplayCutout, WindowMetrics, hustota, rotace 0/90/180/270, skutečné doručení DOWN/MOVE/UP/CANCEL, systémová lišta a bezpečnostní indikátory. Testovat application overlay a pouze s osobním zapnutím uživatelem případný accessibility overlay. Nepředpokládat jeho úspěch ani automatické schválení Google Play.

Výsledek musí být jeden z explicitních režimů:
- **Cutout verified:** minimální bezpečná oblast na zobrazitelném displeji kolem otvoru, pouze po prokázání systémové kompatibility.
- **Safe anchor:** uživatelem zvolená oblast pod lištou; jasně přiznat, že nejde o ovládání samotné čočky. Nesplňuje cutout část plné Definition of Done.
- **Restricted:** chybí vhodný host/oprávnění nebo nelze dosáhnout přijatelné kolize; nevytvořit neviditelnou překážku.

Kalibrace je viditelná jen při nastavování: hranice, test gesta bez vykonání akce, reset, snadné ukončení. Hodnoty ukládat v dp s vazbou na display/rotaci/geometrii, po změně displeje znovu omezit na bezpečné meze. Žádný pevný předpoklad středové kamery nebo rozměrů Nothing. 48–64 dp je výchozí požadavek přístupnosti, nikoliv důkaz bezpečné oblasti; při konfliktu nabídnout větší dosažitelný safe anchor.

## Navržené hranice kódu

Zůstat v existujících modulech. Nové malé třídy vzniknou až při konkrétní etapě, žádný nový Android projekt.

| Hranice | Odpovědnost |
| --- | --- |
| PulseCoordinator + čistý reducer | Stav, capability snapshot, události, lifetime a efekty; bez Views a launch logiky. |
| PulseWindowHost | Přidání/aktualizace/odstranění nejmenšího okna, insets, geometrie, cancellation při změně hosta. |
| PulseGestureRecognizer | Deterministický pointer stream na semantic gesture, žádný přímý launch. |
| PulseEventSource / Media source | Callbacky systému do normalizovaných událostí, korektní odpojení. |
| PulseEventSelector | Stabilní priorita, expirace a výběr uživatele. |
| InteliaActionRegistry + Dispatcher | Jedna definice, validace, preview, revalidace a bezpečné spuštění pro všechny vstupy. |
| Pulse renderer/settings | Nativní zobrazení a editace skutečně dostupných schopností. |

Oddělení je nutné: dnešní služba spojuje mediální přístup, lifecycle, Views a dispatch; pouhá úprava vzhledu neumožní testovat globální gesta a ne-mediální režim bez notification access. Přesto zachovat existující launch/media/icon adaptéry. Nejprve oddělit čisté politiky, potom připojovat hosty. Dočasně nikdy nesmí běžet dva vlastní překryvy téhož účelu.

Lifecycle hostu je součást první technické brány: mediální režim může využít systémem vázaný listener; samostatné akce nesmějí vynucovat přístup k oznámením jen kvůli udržení procesu. Accessibility host pouze při odůvodněném opt-in. Pro trvalý application-overlay režim bez listeneru ověřit platnou service/FGS strategii, start restrictions, typ a případnou viditelnou notifikaci podle cílového SDK; nevymýšlet tichou nekonečnou službu. Není-li vhodný host, schopnost omezit a vysvětlit, nikoliv přidat polling/restart smyčku.

## Stavový automat a priority

Stav = capability gate + presentation + vybraná událost + aktivní pointer session. `HIDDEN` není vypnutí: může existovat explicitně povolený a ověřený hitbox. `DISABLED` nemá okno/listenery. `RESTRICTED` ruší pointer, odstraňuje nebezpečný host a uvádí důvod v nastavení. Zamčení/screen-off ve výchozím návrhu vše skryje; obnovení vyhodnotí aktuální data, ne starý panel.

| Vstup | Přechod / efekt |
| --- | --- |
| Enable + schopný host, bez události | HIDDEN; žádná kresba, animace ani timer. |
| Relevantní událost | COMPACT (krátký souhrn), LIVE pouze pro skutečně aktivní průběh. |
| Tap na událost | EXPANDED, obsah odpovídá aktuálním oprávněním. |
| Tap bez události | Přiřazená akce / malé menu; explicitní preview pokud je vyžadováno. |
| Hold | ARC, stabilní snapshot položek do konce gesta. |
| Close/cancel/expiry | Znovu spočítat nejlepší platnou událost, případně HIDDEN. |
| Lock/revocation/disconnect/rotation | Zrušit rozpracované gesto a neprovést akci; přepočítat capability/host. |
| Disable / hide until unlock | Odebrat i hitbox; hide je dočasná uživatelská restrikce. |

Žádné automatické EXPANDED, ani když prompt v části nastavení zmiňuje automatické rozbalení: bezpečný výklad dovoluje pouze automatické compact/live oznámení. Automatické priority nepřebírají právě ovládaný detail/ARC; nové události čekají. Kritické systémové UI má přednost před celým Pulse, není položkou našeho pořadníku.

Event model: sourceId, eventId, kind, priority, timestamps/expiry, bounded presentation a actionIds. Stabilní pořadí: uživatelova volba během interakce, priorita, původní pořadí při shodě. Žádný automatický carousel. Výchozí návrh: dočasná vlastní karta 60, nabíjení 40, přehrávání 30, pauza 10; konečné priority a timeouty ověřit UX testem. Expiraci řešit jednorázovým časováním jen při živé události, ne pollingem. Po ukončení události vybrat platnou zbývající.

## Gesta a horní ARC

Platform touch slop a long-press timeout jsou základ; konfigurační meze validovat. Jen jeden pointer; další pointer, CANCEL, ztráta hostu a rotace ukončí gesture session. Každé gesto vydá nejvýše jeden action request s unikátním session ID. Pokud je double tap vypnutý, single tap nemusí čekat; zapnutý double tap znamená záměrnou čekací dobu a žádný předběžný single dispatch. Po rozpoznání hold už nikdy nevykonat tap. Swipe down výchozí vypnutý a dostupný jen v ověřeném nekolidujícím režimu.

Horní ARC: původní mělký vějíř směrem dolů, 4–6 stabilních pozic, ve safe area. Výchozí spolehlivá varianta hold → uvolnit → klepnout; kontinuální tah povolit teprve po testu držení stejného pointer streamu při rozšíření okna. Nové okno nemusí převzít probíhající gesto. Hysteresis, průchod segmentu rychlého tahu přes cíl, omezený overshoot a jasná návratová cancel zóna. Uvolnění mimo platný cíl nic nespustí. Při nedostatku místa kompaktní dvouřadé menu, ne namačkané cíle. Dlouhý název ve společném štítku, plný přístupný popis. Spodní LENS ARC geometrii neměnit při této etapě.

## Jednotné akce a kompatibilita FLOW

Navržený ActionDefinition: stabilní lokální ID, revision, typ, název, IconRef, typed payload; potřebné capabilities a confirmation policy odvozovat z typu, nikoliv důvěřovat importu. Binding obsahuje actionId, nikdy kopii payloadu. Změna definice platí pro Search/FLOW/obě ARC/gesta. Editace během preview zneplatní potvrzení a vyžádá nový náhled.

První typy: AppTarget (component + profil), AndroidShortcut (public ID + publisher/profil), FlowReference (existující stabilní záznam), OpenSearch, OpenSettings, podporovaný MediaTransport. System actions jen konečný allowlist skutečně podporovaných API. Žádné shell/generic Intent/string skripty. FlowReference deleguje dnešní runtime, ne druhý sekvenční executor; zakázat cykly a rekurzivní flow-to-flow v první verzi.

Migrovat staré ARC app klíče na odkazy idempotentně a zachovat pořadí; nezměnit serializaci FLOW v1/v2 ani aliasy. Před migrací prověřit konkrétní stabilní ID repository. V1 import → validovaná migrace v paměti → atomický zápis až po úspěchu všech referencí. Neznámý typ zůstane neaktivní/opravením řešitelný, nikdy se nespustí. Bounded payloady/počty, bez osiřelých aktivních bindings. Restore vždy Pulse vypne a nepřenáší systémový souhlas.

Pipeline: resolve → eligibility/permissions → preview → explicit confirmation → re-resolve revision/targets → dispatch → typed result. Chyby MissingTarget, HiddenOrLocked, PermissionRequired, Unsupported, Cancelled a PartialFailure mají srozumitelné UI. FLOW před prvním launch znovu ověří všechny cíle; pokud pozdější dispatch přesto selže, oznámí skutečně provedené kroky, nepředstírá rollback. Nespouštět více aplikací přímo z neviditelného gesta bez stávajícího náhledu.

## Události a osobitý vzhled

Media první: veřejné MediaSession callbacks, stabilní zvolená session a přepínač podporovaných sessions. TransportControls pouze pro podporované bity akcí. Progress počítat z position/speed/updateTime monotónního času, clamp na známou duration; při pauze či neznámém trvání nepředstírat běh. Lokální aktualizace pouze viditelného průběhu, žádné dotazování přehrávače každým snímkem. Artwork bounded async decode/cache, žádné vlastní síťové stahování URI, žádné ukládání historie.

První custom event: **dočasná karta** s názvem, existujícím actionId a dobou platnosti. Uživatel ji explicitně aktivuje, tap otevře kartu, tlačítko vykoná bezpečný dispatcher. Expirace nic nespouští; po restartu žádné předstírání garantované připomínky. Definice se zálohuje, aktivní ephemeral stav ne. Například karta „Cesta“ s FLOW Alive → Můj vlak.

Další zdroje až samostatně: veřejné battery/charging broadcasts; lokální timer/stopky jen s definovanou přesností a lifecycle; downloads pouze vlastní nebo veřejně dostupná data, ne univerzální cizí download tracker. Audio routing neimplikuje dostupnou baterii každého Bluetooth zařízení. Vybrané notifikace vyžadují nový konkrétní souhlas a allowlist; současný media-only slib se nesmí potichu rozšířit. Neimplementované moduly nemají aktivní přepínač.

Identita: prázdný idle; krátké původní párové tahy Intelia při aktivaci, obsahová plocha s mírným nesymetrickým rozvinutím od anchoru, bez neonů a bez kopírování MT Capsule. Pokud kolem čočky není bezpečné místo, obsah pod lištou. System light/dark, Mono, volitelný Material You, vlastní akcent až s kontrastním ověřením. Obsah rozhoduje o velikosti v bezpečných mezích; text 2 řádky/ellipsis, expandovaný obsah scrollovatelný v dostupné výšce, cíle nejméně 48 dp. 200% font scale, landscape a screen reader jsou acceptance testy, ne dnešní tvrzení.

Motion cíle, nikoliv měření: odezva 60–100 ms, compact 150–220 ms, detail 180–280 ms. Interakce nečeká na animaci. Reduced Motion a systémové vypnutí animací; jediná jemná haptika po rozpoznání, respektovat systém. Žádná idle animace ani trvalé fullscreen touch okno.

## Souhlasy a citlivé situace

Nastavení: Obecné/kalibrace, Vzhled, Gesta, Události, ARC, Oprávnění. Zobrazovat funkční schopnosti a konkrétní důvod nedostupnosti. Media potřebují oprávněný session přístup; globální application host overlay permission; případný accessibility host explicitní Android zapnutí a předchozí vysvětlení. Notification access není automatické oprávnění číst všechny notifikace pro nový účel.

Accessibility nepoužívat pro čtení stromů, screenshots, injekci gest nebo autonomní ovládání aplikací. Intelia není automaticky disability support tool: neuvádět nepravdivé isAccessibilityTool. Play disclosure/declaration a review jsou samostatná publikační brána. Per-app exclusions a detekci foreground/fullscreen zpřístupnit jen s prokázaným minimálním zdrojem signálu; bez něj přiznat omezení. Nežádat Usage Access/telefonní oprávnění plošně jen pro heuristiku. Permission dialogy, keyguard, hovory, IME, systémová lišta/QS a chráněné aplikace testovat zvlášť; neprohlašovat univerzální detekci jejich přítomnosti. Nerespektovatelný bezpečnostní konflikt blokuje konkrétní host/režim.

## Etapy pro implementující model

| Etapa | Výstup a podmínka dokončení |
| --- | --- |
| A — capability experiment | Checkpoint, skutečná geometrie/touch/lifecycle evidence obou vhodných hostů, konflikty, zvolený dosažitelný režim. Stop při zásadně nesplnitelném cutout cíli a předložit omezení. |
| B — core + media | Reducer, HIDDEN/COMPACT/LIVE/EXPANDED, teardown, media callbacks, originální renderer a minimální funkční nastavení. Unit/device testy. |
| C — actions + Global ARC | Nejprve registry/dispatch adaptér a migrace, potom bindings/gesta/horní ARC; regressions spodního ARC/Search/FLOW. |
| D — events | Priority selector, jedna kompletní custom karta, charging/battery po ověření API; ostatní zdroje zvlášť. |
| E — polish | Accessibility, vzhled, dlouhý provoz, benchmark, revokace/reboot, GPL source delivery, report. |

Každá etapa má logický ověřený commit, žádný automatický push/merge. Po schválení začít A, ne přepisem celé služby. Nezávislou P0 regresi zaznamenat/řešit minimálně. Neotvírat původní široký task 050. Před každou instalací zachovat package/signature/data; současný release preview může mít vyšší versionCode než debug. Build není důkaz běhu na telefonu. Soukromá záloha zůstává jen na telefonu; žádné pull/overwrite. MT Capsule ponechat; případný konflikt dvou overlayů řešit pouze odsouhlaseným dočasným přepnutím.

## Testovací a výkonnostní plán — vše Pulse 2.0 zatím UNRUN

- Pure unit: přechodová tabulka; priorita/expiry/stabilita; tap/double/hold race, rychlé segmenty, multipointer/CANCEL; validace/import, idempotence migrace a chybějící targets; confirmation revision a no duplicate dispatch.
- Device: idle bez kresby, kalibrace/cutout, touch stream napříč rozvinutím, hold/tap/overshoot/cancel, 4–6 položek a fixní pořadí, rotace, font200%, TalkBack/focus, short/long názvy. Screenshoty jen vlastní UI bez osobních notifikací.
- Media: vlastní bezpečný fixture a reálný podporovaný přehrávač; play/pause/next/previous, více sessions, missing artwork/duration, session death. Neodvozovat reálné audio z fixture.
- Security/lifecycle: lock/unlock, screen off, permission dialog, IME, shade/QS, fullscreen video/hra, hovor, chráněné okno, revokace každého oprávnění, disconnect/reconnect, process death/reboot, úplné vypnutí a odstranění hitboxu/listenerů.
- Regression: HOME role/Recents samostatně, LENS search/swipe, spodní ARC, exact FLOW prefix/preview/launch, aliases, icon packs, wallpaper, restore/migration. Soukromou uživatelskou zálohu nepoužívat jako testovací fixture.
- Performance: stejný optimized build/telefon/refresh/brightness/thermal/network/media workload, střídavé OFF/ON běhy nejméně 3x. Idle CPU/PSS a wakeups, viditelné media, p95/p99/jank/gesture-to-feedback, 100 open/close a heap trend. Idle alespoň 10 min, dlouhý screen-off/audio test alespoň 60 min každá varianta; USB nabíjení zkresluje výdrž, měřit zvlášť bez nabíjení. Žádné resetování celotelefonních statistik bez potřeby/souhlasu. Uvést variabilitu a omezení, žádný slib úspory bez výsledků.

Plná Definition of Done z uživatelova zadání zůstává závazná: fallback anchor sám není splnění cutout cíle. Vyplnit PASS/FAIL/SKIPPED/UNRUN po etapách, screenshot/video relevantních stavů, přesné commands, build/hash/source a otevřená omezení. Dnešní validace je pouze kontrola dokumentace; žádný nový APK/test/performance výsledek.

## Primární technické zdroje

Ověřeno při návrhu 2026-10-15, před implementací platformní hrany znovu prověřit:
- [WindowManager typy a pořadí oken](https://developer.android.com/reference/android/view/WindowManager.LayoutParams): application overlay pod kritickými systémovými okny; accessibility overlay je zvláštní typ vázaný na službu.
- [Display cutout](https://developer.android.com/develop/ui/views/layout/display-cutout): geometrie/insets, nehardcodovat status bar; layout pravidla nejsou oprávnění zachytávat dotyk.
- [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): deklarace, relevantní disclosure/consent a správné označení účelu služby; návrh není garance přijetí do Play.
