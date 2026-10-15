# Pulse 2.0 — návrh a předání implementace

Stav: **etapy A a první ohraničený úsek B implementovány a ověřeny na dostupném telefonu**. 2026-10-15. Podrobné výsledky a zbývající limity jsou v závěrečném acceptance záznamu.
Původní dokumentační brána byla splněna: návrh uložen commitem `1e7311aa1`, poté uživatel napsal „zahaj implementaci“. Task 050 je odložen; žádné jeho změny kódu nevznikly. Zbývající moduly B–E níže jsou návrhem; implementovaný rozsah určují závěrečné ověřené záznamy.

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

## Praktické porovnání MT Capsule — 2026-10-15

Uživatel výslovně povolil aplikaci zapnout, procházet nastavení a zkoušet v praxi.
Inspekce pouze veřejného UI/chování, bez extrakce APK zdrojů, assetů nebo interních
souborů reference. Žádné převzetí grafiky. Větev `codex/pulse-v2-design`, vstupní
HEAD `483070bb4`. V tomto úseku se neměnil kód ani instalace Intelia.

### Web a identita

Oficiální [Google Play](https://play.google.com/store/apps/details?id=com.pryshedko.mtisland&hl=en)
uvádí mediální ovládání, nastavitelné události/gesta a Accessibility překryv nad
stavovým řádkem. To je tvrzení vydavatele, nikoliv nezávislý audit soukromí nebo
spolehlivosti. Na telefonu skutečně instalována `com.pryshedko.mtisland` 17.2,
versionCode 172. Její accessibility host byl během testu ověřen enabled, přístup
mediálního listeneru již dostupný. První otevření ukázalo disclosure požadavku
Accessibility. Nebyly použity žádné shell zápisy do secure permission settings.

### Co bylo skutečně prohlédnuto

- Přehled Mini Capsule Events, Dynamic Cards, Gestures & Cards, Notifications,
  Help/Tips; profil portrét 1080×2392 aktivní, landscape profil 2392×1080 vypnutý.
  Landscape stav se neměnil, proto neslibovat referenční funkčnost v landscape.
- Single tap byl povolen a přiřazen App Search, platí bez aktivní dynamické karty.
  Long press byl přiřazen spuštění Spotify. Double tap stránka výslovně upozorňuje
  na zvýšení globálního zpoždění gest. Přiřazení/uložené hodnoty se neměnily.
- Kalibrace profilu: oddělené Size and position, Collapsed opacity (0 %), border,
  Mini alignment/scale a Expanded mode. V kalibračním UI byly hodnoty X=503,
  Y=30, width=75, height=75 px. Jde o zobrazené hodnoty reference; podle náhledu
  obdélník přibližně vycentruje kameru kolem x540/y68, nejsou to hodnoty středu
  našeho kalibrátoru a nesmějí být slepě zkopírovány. Bez stisknutí Save.
- Player Control: samostatná malá/velká varianta, artwork, ovládací prvky a jejich
  styl, denní/noční náhled, oddělený progress. Vzhled v náhledu není důkaz živého
  přehrávání. Nebyly měněny styly, povolení oznámení ani osobní karty/barcodes.

### Praktické výsledky a meze

| Test | Skutečný výsledek |
| --- | --- |
| MT Test gesture / App Search | Otevřel horní vyhledávací kartu. |
| Klepnutí x540/y98 nad Intelia Home | Otevřelo MT hledání aplikací a IME, tedy globální chování mimo MT activity. |
| Podržení stejné oblasti 850 ms | Přepnulo foreground na Spotify, jak je přiřazeno. |
| Reálné přehrávání | Standardní media PLAY obnovil existující session; MediaSession stav PLAYING. MT zobrazila malou kapsli uvnitř status baru, nikoliv starý Intelia panel pod ním. |
| Klepnutí na aktivní mediální kapsli | Rozbalilo artwork/title/artist a previous/pause/next nad vlastní plochou; vlastní původní screenshot ověřen. |
| Pauza | První pokus nebyl potvrzen (session ještě PLAYING). Při opakování po čerstvém rozbalení byl pozorován PAUSED. Následně explicitní standardní PAUSE garantoval konečný paused stav. Pozdější snímek zachytil přechod systémového UI, proto neodvozovat univerzální bezkoliznost ani dokonalé dismissal chování. |
| Swipe do systémové lišty mimo kameru | NotificationShade isOnScreen=true/isVisible=true, bez čtení obsahu oznámení. |
| LENS při přechodu lišty/overlay | Automatická kontrola nenašla vlastní UI; UNVERIFIED, nikoliv důkaz regrese Intelia nebo spolehlivého pass-through. |
| Syntetická MediaSession fixture bez audia | Nevyvolala viditelnou MT media kartu; nelze považovat za reálný mediální test. Vlastní dočasná testOnly aplikace následně odinstalována, absence ověřena. |
| Zámek, hovory, chráněné dialogy, jiné OEM, baterie | UNRUN. |

Pokus o dump celé Spotify UI zastavila automatická kontrola kvůli potenciálně
soukromým údajům. Nebyl proveden ani obcházen; bezpečná náhrada byla standardní
media key bez čtení knihovny/playlistů. Nedošlo k nákupu, přihlášení, změně playlistu
ani kopírování soukromé zálohy. Screenshoty referenčního UI zůstávají jen v místních
pomocných souborech mimo Git; nejde o assety pro Intelia. Nechtěný pozdější snímek
systémového UI byl odstraněn. Bez nových výkonnostních tvrzení.

Konečný stav: Intelia stále výchozí HOME; přehrávání PAUSED; MT ponechána instalovaná
s povoleným hostem a původními profily/vazbami. Původní Intelia Pulse pouze skryt
svým existujícím „do dalšího odemčení“ (enabled preference true, hidden true,
observing false/window false v ověřeném okamžiku). Po odemčení se může stará kapsle
obnovit; při příštím srovnání znovu předejít dvojímu překryvu. Kalibrace není otevřená.

### Opravené zadání vlastní implementace

1. **Oddělit čtyři geometrie:** přesný vizuální cutout/čočka, minimální transparentní
   aktivační region, kompaktní událost a rozbalený detail. Dnešní testovací 48dp
   obdélník není finální vizuální velikost čočky. Nespojovat šířku karty s touch slop.
2. Kalibrace určuje skutečný hardware střed/tvar/rozměr a viditelný náhled existuje
   pouze během nastavování. Umístění near-camera se ověřuje nezávisle od přístupnosti
   aktivačního cíle. Pokud plných 48 dp u lišty koliduje, nabídnout kompaktní přesný
   region s jasnou dosažitelnou 48dp alternativou v Intelia/LENS, ne tajně rozšířený
   neviditelný blok přes systémové prvky. Konkrétní kompromis ověřit accessibility
   testem; žádná deklarace, že malý cíl automaticky splňuje doporučení 48 dp.
3. HIDDEN nemá viditelný obdélník/šrafy/obrys. Jen explicitně povolený minimální
   aktivátor. Aktivní media → malý obsah v bezpečném místě u čočky; ne trvalá velká
   titulková karta pod statusem. Při nedostatku prostoru omezit obsah/host/režim.
4. Tap otevře aktivní relevantní detail; bez události vlastní uživatelovu akci.
   Hold otevře náš horní ARC; stabilní pozice, CANCEL nic nevykoná. Přiřazení přes
   společný Action Engine/FLOW, nikoliv kopie těchto konkrétních MT vazeb.
5. Detail pouze po interakci, zavření mimo/Zpět/systémový přechod bezpečně otestovat.
   Žádný trvalý fullscreen touch blocker. U skutečné karty přesné obslužné regiony;
   nestačí transparentní pixely na celé obrazovce. Neřešit kolize vypínáním systémové
   lišty, privacy indikátorů, IME nebo cizích aplikací.
6. Vlastní vzhled: původní párové tahy Intelia, klidná geometrie, čitelný kontrast,
   artwork zdrojové aplikace, omezené akcenty a krátké přechody. Nepřebírat referenční
   červený kalibrační kruh, karty, tvary tlačítek nebo jeho názvosloví/grafiku.
7. „Nesmí překážet“ je testovatelná acceptance matice, nikoliv absolutní slib:
   mimo region běžné ovládání, shade vedle čočky, IME, lock/security restrictions,
   fullscreen/rotace a dostupná alternativní aktivace. Kritický konflikt blokuje host.

Další nejmenší úsek: etapě B předřadit produkční kontrakt oddělené geometrie a
kalibrace (validace/migrace/backup), potom bounded accessibility host a HIDDEN +
jedna reálná media karta. V této srovnávací etapě nebyla přepsána produkční služba.

## Stage B first executable slice — 2026-10-15

User authorized production implementation, personally enabled the new Intelia
Pulse accessibility service, temporarily disabled MT and saved production camera
calibration. MT remains installed with its settings. Debug automatic geometry is
not reused as physical proof. User-confirmed portrait profile survives APK updates.

Implemented files: PulsePolicy + tests, production calibration activity/controls,
InteliaUiPreferences backup/profile validation, bounded Pulse accessibility host,
minimal Island media permission bridge, Settings/Home destinations and manifest/
localized resources. Hardware shape, transparent activation, compact media and
expanded content are separate. Idle paints nothing. On the tested portrait profile
activation is 84x84px; active media is 168x79px, centered at the camera. Detail starts
below status/cutout insets; no fullscreen scrim. Hold is release-then-tap, not drag.
System-window events conservatively hide the host; no application content retrieval,
notification payload callbacks, gesture injection, polling or foreground worker.

Tap idle opens existing Intelia search. Hold opens five stable eligible app slots,
search/settings and up to six existing FLOW aliases. Multi-step aliases show an
ordered preview, then require explicit Run through the existing guarded runtime.
Real public media session supplies compact artwork/glyph and explicit detail with
supported previous/play/pause/next controls. Large bitmaps are rejected (>1024px).
Preview calibration is non-touchable; only Done persists. Backup restores Pulse off
and camera profiles unconfirmed. No reference source/artwork or new dependency.

### Verification / candidate evidence

Final command PASS (BUILD SUCCESSFUL, 23s; 1042 tasks):
`gradlew.bat -p Intelia-Launcher :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :app:app:assembleDefaultDebug --offline --console=plain --max-workers=2 --no-problems-report`.
35 UI + 20 search-action tests PASS; 0 failure/error/skip. An initial unprivileged
Gradle daemon startup failed in the restricted environment; same local offline
build succeeded with approved toolchain access. Initial MediaController import
ambiguity was repaired before the passing build.

APK `app/app/build/outputs/apk/default/debug/app-default-debug.apk`:
SHA256 `dbbd4f54f551135ef54d29d0a0384f996abcdd8f2cff1e8c3092ed7cac57d7ec`.
Embedded source SHA256 `289fe5c3de87bb352cad999782932c76eaa4dfee7523d53d9354f7324be5f05b`.
APK/source CRC, exact byte match for all 14 changed/new application source files,
license/attribution presence and absence of keys/APKs/private backups in bundled
source PASS. Local helper scripts, device IDs, captures and media metadata excluded
from Git. `git diff --check` PASS (line-ending notices only).

Device: Nothing A059 / Android 16 API36, 1080x2392, 2026-10-15. `adb install -r`
PASS, data and HOME holder retained. User saved production alignment. Injected
idle tap -> own search PASS; hold -> bounded ARC PASS; cesta -> ordered two-app
preview without dispatch PASS; outside dismissal PASS. Playback -> COMPACT,
tap beside physical hole -> EXPANDED, own pause -> HIDDEN PASS. Playback finally
paused. Shade swipe beside camera -> RESTRICTED/window=false/observing=false,
collapse + Home -> HIDDEN PASS. No notification contents read for this check.

Two discovered issues were fixed and rechecked: Home return could leave a safe
restriction latched; an already-focused Home intent now explicitly re-arms it.
Replacing the camera window during long-press DOWN could hand the remaining stream
to SystemUI; hold now marks intent, dispatches only on UP, CANCEL/multipointer does
nothing. Final ARC has flat ripple controls, no stock button elevation. A physical
cutout is not tappable glass: media was verified beside it, not through the lens.
UI-automation queries can interrupt accessibility host observation; final bounded
UI checks used own screenshots and service diagnostics instead of cross-app dumps.
An accidental shade screenshot from the failed transition was deleted locally.

### Remaining acceptance boundaries

This is the first stage B slice, not all Pulse 2.0. Physical finger acceptance after
this final gesture change, final-host lock/unlock/call/security/IME/fullscreen,
TalkBack/large-font, other OEMs/resolutions/rotations and battery/latency measurements
are UNRUN. Package-based system restrictions are best effort, not universal proof
of non-obstruction. Other rotation/aspect profiles require user confirmation.
Back dismissal is not globally intercepted; close/outside/timeout/window transition
are available. No continuous camera-to-ARC selection. Generic action registry,
configurable gesture bindings, priority/event modules, session picker/progress and
broader media artwork support remain future work. No Google Play policy approval
or release performance claim. Next smallest task: physical gesture and security/
rotation acceptance on this bounded host before adding more event types.

## Acceptance follow-up and optimized preview — 2026-10-15

User requested completion of remaining stage B checks. Physical idle tap, hold/
release/ARC and shade beside the lens were explicitly confirmed OK by the user.
Device verification exposed and repaired three issues: unconfirmed rotation kept
media observation attached; 180-degree rotation did not cause a configuration
callback; unlock left SystemUI restriction latched until Home. The host now gates
observation/dispatch by confirmed current geometry, observes DisplayManager changes
(including 180 degrees), cancels pending icon work/timeouts under restriction, and
tracks the last app restriction separately from temporary SystemUI/keyguard cover.
Unlock restores an ordinary app but preserves Settings/default-phone restrictions.
The default dialer is resolved using public TelecomManager.getDefaultDialerPackage,
without phone-state/contact/call-log permissions. Known call/permission/system
packages are also conservatively restricted. No incoming/outgoing call was placed.
API reference: https://developer.android.com/reference/android/telecom/TelecomManager#getDefaultDialerPackage()

PASS on Nothing A059/API36: Settings hides window and observation, Home restores;
physical gesture acceptance; actual Chrome launch from ARC without reading its UI;
screen-off removes window/observation; user unlock restores Pulse automatically
without pressing Home (final release candidate); default dialer screen hides Pulse
without dialing; four actual display rotations 0/1/2/3, unconfirmed orientations
have window=false/observing=false and portrait restores. A temporary testOnly local
media/fullscreen fixture drove rotation only in its own activity; global auto-rotate
remained 1. A tap outside the compact capsule reached that fixture (own UP log).
Fixture was uninstalled and package absence verified. User apps/data/backup retained.
No AndroidRuntime error in final own-process log. These observations are not a
universal security guarantee or a battery benchmark.

Final PASS command (3m15s, 2870 tasks):
`gradlew.bat -p Intelia-Launcher :app:ui:testDebugUnitTest :data:search-actions:testDebugUnitTest :app:app:assembleDefaultDebug :app:app:assembleDefaultRelease "-Pintelia.localPreview=true" --offline --console=plain --max-workers=2 --no-problems-report`.
39 UI + 20 search-action tests PASS, zero failure/error/skip. Added geometry edge/
density/screen-shape and profile-key isolation cases, system/default-dialer policy,
and unlock/restriction transition coverage. Initial unquoted PowerShell property
invocation failed before tasks; quoting fixed it. Final builds are successful.

Installed non-debuggable optimized APK with `adb install -r`:
`app/app/build/outputs/apk/default/release/app-default-release.apk`.
APK SHA256 `4454677d1b3b8c2eb93ddc44f572838742058b287fa38f357860961e0a7d6065`.
Embedded source SHA256 `512524846b4ce838999beab82bfc230b3d83b8ce807900459b0e3b955a8c36f1`.
Package remains dev.intelia.launcher.debug, matching the installed signing identity;
HOME role retained, production calibration preserved. Signature verification and
non-debuggable manifest check passed for optimized build. Exact 15 relevant source
files/CRC/licenses PASS; repository scan of 2181 files found no secret-pattern hits
or unexpected artifacts, APK 1034 entries had no unexpected private artifacts.
No screenshot/device ID/private backup or test fixture entered Git. No push/merge.

Stage B acceptance on available hardware is closed with explicit limits: live call,
other OEMs/devices, physical calibration of other rotations, TalkBack/200% fonts and
battery/latency benchmarks remain UNRUN. Do not claim all Pulse 2.0 modules complete.
Next independent task is accessibility/other-device acceptance; generic registry,
new event types, session picker/progress/custom bindings remain separate proposals.
