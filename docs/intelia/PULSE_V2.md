# Pulse 2.0 — návrh a předání implementace

Stav: **uživatel potvrdil implementaci; probíhá pouze etapa A**. 2026-10-15.
Původní dokumentační brána byla splněna: návrh uložen commitem `1e7311aa1`, poté uživatel napsal „zahaj implementaci“. Task 050 je odložen; žádné jeho změny kódu nevznikly. Etapy B–E níže zůstávají návrhem, nikoliv implementovaným stavem.

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

## Testovací a výkonnostní plán

Produkční Pulse 2.0 je zatím UNRUN/neimplementovaný. Výjimkou jsou níže uvedené výsledky debug experimentu etapy A a navazující ruční kalibrace; nelze je přenášet na dokončený Pulse, výkon nebo fyzickou ergonomii.

- Pure unit: přechodová tabulka; priorita/expiry/stabilita; tap/double/hold race, rychlé segmenty, multipointer/CANCEL; validace/import, idempotence migrace a chybějící targets; confirmation revision a no duplicate dispatch.
- Device: idle bez kresby, kalibrace/cutout, touch stream napříč rozvinutím, hold/tap/overshoot/cancel, 4–6 položek a fixní pořadí, rotace, font200%, TalkBack/focus, short/long názvy. Screenshoty jen vlastní UI bez osobních notifikací.
- Media: vlastní bezpečný fixture a reálný podporovaný přehrávač; play/pause/next/previous, více sessions, missing artwork/duration, session death. Neodvozovat reálné audio z fixture.
- Security/lifecycle: lock/unlock, screen off, permission dialog, IME, shade/QS, fullscreen video/hra, hovor, chráněné okno, revokace každého oprávnění, disconnect/reconnect, process death/reboot, úplné vypnutí a odstranění hitboxu/listenerů.
- Regression: HOME role/Recents samostatně, LENS search/swipe, spodní ARC, exact FLOW prefix/preview/launch, aliases, icon packs, wallpaper, restore/migration. Soukromou uživatelskou zálohu nepoužívat jako testovací fixture.
- Performance: stejný optimized build/telefon/refresh/brightness/thermal/network/media workload, střídavé OFF/ON běhy nejméně 3x. Idle CPU/PSS a wakeups, viditelné media, p95/p99/jank/gesture-to-feedback, 100 open/close a heap trend. Idle alespoň 10 min, dlouhý screen-off/audio test alespoň 60 min každá varianta; USB nabíjení zkresluje výdrž, měřit zvlášť bez nabíjení. Žádné resetování celotelefonních statistik bez potřeby/souhlasu. Uvést variabilitu a omezení, žádný slib úspory bez výsledků.

Plná Definition of Done z uživatelova zadání zůstává závazná: fallback anchor sám není splnění cutout cíle. Vyplnit PASS/FAIL/SKIPPED/UNRUN po etapách, screenshot/video relevantních stavů, přesné commands, build/hash/source a otevřená omezení.

## Etapa A — ověřený debug experiment 2026-10-15

Zdrojový checkpoint `09bf44885`, dokumentace `1e7311aa1`; větev `codex/pulse-v2-design`. Draft PR #3 byl ověřen OPEN/draft, stále pro původní `codex/intelia-wallpaper-design` proti `feat/flow-aliases`; žádný push/merge. Nová geometrie je čistá Kotlin politika, bez práva ovládat systémovou lištu. Debug activity/service/manifest/resources jsou pouze v `src/debug`. Produkční preference, FLOW a HOME manifest se nezměnily. Produkční Pulse služba dostala pouze dočasnou nepersistovanou brzdu po dobu otevřené debug kalibrace: její vlastní mediální kapsle zakrývala kalibrační údaje. Pause kalibrace brzdu zruší a požádá stávající refresh; běžný release nemá žádného volajícího, který ji aktivuje. Kalibrace nevykonává žádné akce, nic nečte z oznámení/cizích oken a neukládá historii.

Uživatel osobně zapnul volitelnou debug accessibility službu a potvrdil „Povoleno, pokračuj v testu u čočky“. Služba má `canRetrieveWindowContent=false`, bez event types, screenshot/gesture capabilities či global actions. Kalibrační okno existuje jen během vlastní activity, výchozí rozměr je 48 dp × 48 dp (126 px při density 2.625), s viditelným tyrkysovým obrysem. První experiment měl 30sekundový timeout; ruční kalibrace jej prodloužila na 2 minuty, změna posuvníku obnoví náhled. To není finální neviditelný režim. Při pause/rotaci se ruší; keep-awake platí jen pro otevřenou debug activity, ne systémový timeout.

Nothing A059: 1080 × 2392, density 2.625. Portrét: DisplayCutout rect `(454,0)-(540,126)`, status inset 126 px; camera anchor `(434,0)-(560,126)`, safe anchor `(434,126)-(560,252)`. Jde o API geometrii, nikoliv důkaz fyzické dotykovosti otvoru.

| Ověření | Výsledek |
| --- | --- |
| Application overlay / camera | 0 DOWN / 0 UP ze 2 vložených tapů; vykreslení samo nestačí. |
| Application overlay / pod lištou | 2 DOWN / 2 UP ze 2 vložených tapů. |
| Accessibility overlay / camera | 2 DOWN / 2 UP; prokázané doručení s uživatelským grantem. |
| Accessibility drag v portrait | 3. DOWN, 26 MOVE, poté CANCEL místo UP; nelze spoléhat na nepřerušitelné tažení. |
| Rotace 0/1/2/3 | Po ustálení 2 vložené tapy → 2 DOWN / 2 UP v každé. |
| Mimo hitbox | Vlastní čítače zůstaly 0; neprokazuje kompletní kolizní matici jiných aplikací. |
| Home teardown | Kalibrační okno v WindowManager již neexistovalo. |
| Fyzické zarovnání původního automatického návrhu | FAIL podle uživatele: kamera je jinde. Samotný OEM bounding rect nestačí. |
| Fyzické tap/hold a lišta vedle nové ruční oblasti | Čeká na zarovnání a ověření uživatelem. |
| Invisible idle, protected dialogs, hovory, IME, reboot, energie | UNRUN; není produkční host. |

Rotované cutout rects: 1=`(0,540)-(126,626)`, 2=`(540,2266)-(626,2392)`, 3=`(2266,454)-(2392,540)`. Anchor zůstal v obrazovce. První landscape pokus během přechodu nedoručil tap; opakování po ustálení prošlo. Testy musí čekat na stabilní rotaci/layout/input, nikoliv jen dokončení `am start`.

Přesné finální sestavení: `gradlew.bat :app:ui:testDebugUnitTest :app:app:assembleDefaultDebug --offline --console=plain --max-workers=2 --no-problems-report`, lokální JDK21/SDK/Gradle cache z WORKFLOW. PASS, 17 s; 1037 tasks: 12 executed, 1025 up-to-date. UI JUnit XML: 27 tests, 0 failure/error/skip; z toho 5 nových geometrických. Předchozí kompilační chyba nullable `Activity.dump` byla opravena před instalací finálního kandidáta.

Zařízení: `adb install -r <debug.apk>` PASS, shodný signing certificate ověřen přes `apksigner verify --print-certs` s předchozím preview. `am start -n dev.intelia.launcher.debug/dev.intelia.launcher.ui.PulseProbeActivity --ei rotation <0..3> --es probe <application|safe|accessibility>`; diagnostika přes `dumpsys activity <component>`, dotyky přes `input tap` / `input swipe`. Žádné globální rotation/font/timeout nastavení se neměnilo. APK/source ZIP CRC PASS, šest nových source souborů byte-identických v GPL zdrojovém balíčku, licence/attribution přítomny, bez soukromých záloh/klíčů.

První automatický debug kandidát APK SHA256 `f19692be9fddd5da3a4419d8fbba88d41651ca85c771ae820a1bdb48a241ed45`; source ZIP `21dc6db88b2ec381b8beb76632639920670ca48ebd9ecc9707324008b2033bc5`. Je nahrazen ruční kalibrací popsanou níže. Předchozí podepsané preview uloženo soukromě pro rollback, SHA256 `bc90ff9c3509d9c452b907c1048c75f8f5d320e3ea42afe884228771899798e4`. Uživatelská záloha zůstala pouze v telefonu.

Závěr pro další etapu: application host má dostupný safe anchor; camera varianta potřebuje ověřený accessibility host. CANCEL musí vždy skončit bez dispatch. Předběžně zvolit horní ARC hold → release → tap, nikoliv slib kontinuálního tahu. Plný „Cutout verified“ režim zatím neoznačit za dokončený: fyzická ergonomie, přístupnost a citlivé systémové situace zbývají.

### Uživatelská korekce: ruční kalibrace kamery

Uživatel výslovně odmítl polohu odvozenou z OEM API a vyžádal vlastní nastavovací program. Implementován původní nativní kalibrátor: křížek/střed, posuvníky X/Y, šířka a výška, jemné šipky po 1 px, automatické lokální ukládání a explicitní reset na Android návrh. Reset se nikdy neprovede automaticky po opětovném otevření. Technické údaje/testy jsou schované za přepínačem, ovládání je česky, rozložení respektuje insets a nemá pevné pixelové paddingy pro Nothing.

Profil je v oddělených debug SharedPreferences, klíč version/displayId/rotation/aspect; středy jsou normalizované, rozměry v dp. Změna rozlišení při stejném aspectu může zachovat relativní střed; jiná geometrie dostane samostatný profil. Souřadnice nezávisí na umístění či existenci cutoutu. Rozměry se omezují na displej, minimální cíl 48 dp; výška nejvýše třetina displeje, žádné fullscreen okno. To umožňuje ruční nastavení různých zařízení, není to důkaz funkčnosti na každém OEM. Nevalidní/nonfinite profil neaktivuje chybnou geometrii. Debug profil zatím není součást `.intelia`: teprve etapa B jej musí migrovat do validovaného produkčního config/backup kontraktu. Neoznačovat tuto mezeru za hotovou personalizaci Pulse 2.0.

Finální stejný build command PASS, 17 s, 1037 tasks (12 executed / 1025 up-to-date). **31 UI unit testů PASS**, 0 skip/failure/error; 9 geometrických včetně ručního středu, změny rozlišení, clamp/NaN a pixelového float round-trip. Device PASS: pravá šipka posune skutečné WindowManager okno o právě 1 px; po Domů a znovuotevření poloha zůstane; levá šipka vrátí testovací posun. Zachována před testem nastavená poloha uživatele. Screenshot pouze vlastní kalibrace vizuálně zkontrolován, současný Pulse již nezakrývá ovládání. HOME role zůstala `dev.intelia.launcher.debug`. Žádné mazání dat, žádné změny cizích aplikací, žádný push/merge.

Nainstalovaný APK SHA256 `66714df0e6851716e56dab38ad481588eb2bad00e3f64aadee68b1f593cef865`, source ZIP `ee77a25c70028209aac453c3c9a90462a5822a8ff178c2f1b33d1087573faa22`. CRC/source audit PASS, 8 relevantních source souborů byte-identických, licence přítomny. Finální ruční UI nebylo znovu otestováno na všech rotacích ani na 200% font scale/TalkBack; tabulka rotací výše patří předchozímu kandidátu. Na telefonu zůstala otevřená ruční kalibrace pro uživatelovo zarovnání. Další krok: skutečné fyzické zarovnání a dotyky, potom uzavření A a teprve B.

## Primární technické zdroje

Ověřeno při návrhu 2026-10-15, před implementací platformní hrany znovu prověřit:
- [WindowManager typy a pořadí oken](https://developer.android.com/reference/android/view/WindowManager.LayoutParams): application overlay pod kritickými systémovými okny; accessibility overlay je zvláštní typ vázaný na službu.
- [Display cutout](https://developer.android.com/develop/ui/views/layout/display-cutout): geometrie/insets, nehardcodovat status bar; layout pravidla nejsou oprávnění zachytávat dotyk.
- [Google Play AccessibilityService policy](https://support.google.com/googleplay/android-developer/answer/10964491?hl=en): deklarace, relevantní disclosure/consent a správné označení účelu služby; návrh není garance přijetí do Play.

The calibrator is now discoverable in the native Settings Pulse group through
**Kalibrace čočky** even with Pulse disabled. The entry is capability-gated on the
actual installed activity, so release builds without the debug activity show no
inert control. Localized launch errors are displayed. Final incremental build and
source/XML audit PASS; installation and direct opening PASS. End-to-end entry UI
check interrupted by another foreground app and remains UNVERIFIED. Previous
artifact hashes above identify their historical candidates, not this update.
