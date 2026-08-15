# Vault-Tec Launcher (fork de Olauncher)

Réplica en Android del wallpaper de Fallout/Vault-Tec del PC
(`FalloutWallpaper-Anki-GYM`): un launcher minimalista que muestra el estado
de tu vida real leído del **vault de Obsidian** sobre una pantalla tipo
Pip-Boy (VT323, verde/ámbar/negro), y que permite **ocultar apps sin ninguna
forma de revelarlas** desde el launcher.

Fork de [Olauncher](https://github.com/tanujnotes/Olauncher) (GPL-3.0). El
código de Olauncher se mantiene intacto salvo los cambios documentados aquí.

---

## El proyecto en el PC que replicamos

- `server/serve.py` sirve `fallout-stats.html` con `/api/gym` (tablas de
  `GYM/{Push,Pull,Leg}/*.md`), `/api/points` (bloque JSON de `points.md`, rachas
  0–365), `/api/read` (checkboxes de `Read/Read.md`).
- En el celular el launcher hace lo mismo pero **SÓLO LECTURA**: lee los
  mismos `.md` vía el almacenamiento compartido (SAF) y los muestra como filas
  SPECIAL (G/V/M/D/C/R) con su barra y detalle (progresión en kg del GYM del
  día, libros en READ, racha del día).

## Cómo se vinculan los datos (arquitectura)

```
  PC (vault git)  ⇄  Syncthing (P2P, segundos)  ⇄  Cel ···/Documents/Obsidian
                                                          │
            Vault-Tec Launcher  [SOLO LECTURA]            │
              SAF folder-picker → Documents/Obsidian/Me   ▼
              lee GYM/*.md, points.md, Read/Read.md
```

- **Single source of truth:** el vault de Obsidian. El launcher no escribe nada
  (los puntos/rachas se siguen marcando desde el PC).
- **Syncthing** reemplaza al sincronizador git+Termux en el celular (segundos,
  sin commits). La carpeta compartida es el vault completo, ignorando `.git/`
  y `.obsidian/` para evitar churn.
- El launcher guarda la **carpeta del vault** con permisos SAF persistentes
  (`VAULT_TREE_URI`).

### Instalación / primer uso

1. Instala el APK (`com.vaulttec.launcher`).
2. Pon el vault en el celular (Syncthing → `Documents/Obsidian`).
3. Abre el launcher → toca el prompt **"TOCA PARA CONECTAR EL VAULT"** →
   selecciona la carpeta **`Me`** del vault.
4. Gestos (igual que Olauncher): swipe arriba = apps, swipe abajo =
   notificaciones, swipe izq/der = apps propias, long-press = settings,
   doble-tap = bloquear (opcional).
5. El launcher se refresca al volver a home y cada 60 s (o tocando el título
   "VAULT-TEC P.I.P.").

## Ocultar apps SIN ruta de revelado

Olauncher oculta apps desde el cajón (**long-press sobre una app → Hide**) y
las guarda en `Prefs.HIDDEN_APPS`. La única manera de *verlas de nuevo* era la
entrada **"Hide apps"** de Settings (que también se abría tocando el logo
"Olauncher" arriba de Settings). En este fork:

- Se eliminó el handler `olauncherHiddenApps -> showHiddenApps()` y la función
  `showHiddenApps()` (`SettingsFragment.kt`).
- Por tanto **una vez oculta una app, no hay ninguna vía en el launcher para
  volver a verla o desocultarla**. El diálogo del primer hide ya no revela
  instrucciones.
- Los cambios de fase 1 se aplican a `layout/` y `layout-land/` (para que
  ViewBinding no marque las vistas como opcionales).

Limitaciones honestas: es ocultación a nivel de launcher. Las apps siguen
estando en `Ajustes → Apps` (bloquéalas con tu AppLock) y borrar los datos del
launcher recetearía la lista de ocultas. Para bloqueo absoluto sin root hace
falta device-owner (no usado aquí, por decisión).

## Cambios sobre Olauncher v6.7.19

| Área | Cambio |
| --- | --- |
| `app/build.gradle` | `applicationId com.vaulttec.launcher`, `versionName v0.1.0`, dep `androidx.documentfile` |
| `data/Prefs.kt` | pref `VAULT_TREE_URI` |
| `data/VaultData.kt` | **nuevo**: `VaultRepository` (lectura SAF gym/points/read) + `VaultLogic` (SPECIAL, rachas, día GYM, alerta) — port de `serve.py` |
| `ui/VtTheme.kt` | **nuevo**: paleta Pip-Boy + fuente VT323 |
| `ui/HomeFragment.kt` | home Pip-Boy (SPECIAL + detalle GYM/READ + header ERROR), picker SAF, refresh 60 s |
| `fragment_home.xml` (+land) | bloque `vtHeader/vtConnect/vtSpecialList/vtDetail` |
| `ui/SettingsFragment.kt` | **anti-revelado**: quitada entrada "Hide apps" |
| `assets/fonts/VT323-Regular.ttf` | fuente generado del sistema |

Anki (GERMAN/HACKERMAN) queda fuera en Android por ahora; las filas E/H no se
muestran.

## Build

```bash
cd ~/Proyects/Olauncher
./gradlew assembleDebug        # APK: app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Configurar Syncthing

- **PC (Arch/omarchy):** `sudo pacman -S syncthing && systemctl --user enable --now syncthing`
  → `http://127.0.0.1:8384` → añadir carpeta `~/Documents/obsidian`.
- **Celular:** instalar **Syncthing-Fork** (F-Droid) → escanear QR/código del PC
  → carpeta destino `Documents/Obsidian`.
- Ignore patterns de la carpeta del vault (ambos lados):
  `/.git/`, `/.obsidian/`, `/.trash/`.

## Estado

- [x] Fase 0: clon + build verificado
- [x] Fase 1: anti-revelado de apps ocultas
- [x] Fase 2: lectura del vault (SAF) + parsers gym/points/read
- [x] Fase 3: UI Pip-Boy (VT323, SPECIAL, detalle, header ERROR)
- [ ] Fase 5: QA en dispositivo real (instalar, conectar vault, verificar que
  ocultar sea irreversible)