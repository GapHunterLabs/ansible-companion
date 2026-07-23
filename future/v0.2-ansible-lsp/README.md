# v0.2.0 — FQCN + Jinja2 + scope de YAML (en pausa)

Este codigo compila y sus tests pasan (9/9 al momento de apartarlo — mas
23/23 nuevos de `runtime/`, ver abajo), pero se saco de `src/main/kotlin`
para que v0.1.x (solo vault) no dependa de LSP4IJ ni del plugin de YAML —
asi `verifyPlugin` no marca una referencia a clases de otro plugin sin
declarar la dependencia.

## Bloqueador de Node — RESUELTO (2026-07-23)

El bloqueador real que mantenia esto en pausa (ver ARCHITECTURE.md, sección 2)
era **empaquetar Node + `@ansible/ansible-language-server` dentro del
plugin**, sin pedirle al usuario que instale nada — Ansible tampoco corre en
Windows nativo (confirmado a mano: falla al importar `fcntl`), asi que "instala
Node/Ansible tu mismo" no es una salida para buena parte de los usuarios reales.

Diagnostico clave: el paquete del language server + todo su arbol de
dependencias (`@flatten-js/interval-tree`, `antsibull-docs` —una
reimplementacion en TypeScript, no shell-out a Python—, `axios`, `glob`,
`vscode-languageserver`, etc.) pesa **~16MB y es 100% JS puro, sin addons
nativos** (`find node_modules -name "*.node"` vacio) — eso SI se puede
bundlear directo dentro del plugin sin problema de portabilidad entre
sistemas operativos.

Lo que NO se puede bundlear igual es el binario de **Node** en si: es nativo
y distinto por plataforma+arquitectura (win/linux/mac × x64/arm64 = 6
combinaciones, ~80-110MB cada una sin comprimir) — meter las 6 en el mismo
`.zip` infla el plugin a 500MB+ para una feature que la mayoria de
instalaciones no usa el mismo dia que instalan. La solucion, y el patron que
ya usan otros plugins de IntelliJ que envuelven herramientas externas (ej.
Rust/rust-analyzer): **descargar y cachear un runtime de Node privado por
usuario, la primera vez que se necesita, verificado por checksum antes de
tocar el contenido.**

### `runtime/` — implementado, testeado, y verificado contra nodejs.org real

| Archivo | Que hace |
|---|---|
| `NodePlatform.kt` | Deteccion de plataforma (inyectable, testeable sin depender del OS que corre el test) + tabla de URL/checksum SHA-256 pineados a mano para Node **v24.18.0** (LTS "Krypton"), las 6 combinaciones win/linux/mac × x64/arm64. |
| `ArchiveExtractor.kt` | Extrae **un solo archivo** (el binario `node`/`node.exe`) de un `.zip` o `.tar.gz` sin descomprimir el resto del arbol (npm/docs/headers que nunca se usan) — reader de TAR (ustar + extension GNU de nombre largo) escrito a mano en vez de agregar Apache Commons Compress como dependencia nueva (mismo criterio que `AnsibleVaultCipher`: PBKDF2 a mano en vez de una libreria). |
| `NodeRuntimeProvisioner.kt` | Orquesta: si ya esta cacheado y verificado, devolver esa ruta; si no, descargar a un archivo temporal → **verificar SHA-256 antes de extraer o ejecutar nada** → extraer solo el binario → marcar `.provisioned` → limpiar el archive temporal. Nunca confia en contenido sin verificar. |

`runtime-tests/` — 23 tests JUnit, todos pasando: deteccion de plataforma,
extraccion de zip/tar.gz (incluyendo el caso de nombre largo GNU y "entry no
encontrado"), y el flujo completo de `NodeRuntimeProvisioner` contra un
servidor HTTP local real (`com.sun.net.httpserver.HttpServer`, sin red real,
sin depender de los checksums verdaderos de Node que por diseño no se pueden
falsificar en un test) — cubre happy path, cache-hit en la segunda llamada,
checksum invalido (no debe dejar nada extraido en disco), HTTP 404, y entry
ausente dentro de un archive por lo demas valido.

**Ademas verificado a mano, una vez, contra la red real** (no es parte del
test suite automatizado — descarga ~90MB real, no es apto para CI): un
`main()` de humo (`SmokeTestMain.kt`, no incluido aca a proposito — vivio
solo en un proyecto Gradle descartable en el scratchpad de la sesion que
escribio esto) hizo `NodePlatform.detect()` sobre el `os.name`/`os.arch` real
de la maquina, descargo `node-v24.18.0-win-x64.zip` de `nodejs.org` de
verdad, verifico su SHA-256 contra el pineado, extrajo solo `node.exe`
(92 534 088 bytes — el binario solo, no el arbol completo), lo corrio con
`--version` y confirmo `v24.18.0`, y confirmo que una segunda llamada usa el
cache (1ms vs ~8s la primera). Para reproducirlo: crear un proyecto Gradle
Kotlin standalone con estos 3 archivos + un `main()` que llame
`NodeRuntimeProvisioner(tempDir).ensureProvisioned(platform)` y ejecute el
binario resultante con `--version`.

`AnsibleLanguageServer.kt`/`AnsibleLanguageServerFactory.kt` ya estan
actualizados para usar el provisioner (via `PathManager.getSystemPath()` como
cache root) en vez de asumir `node` en el PATH del sistema.

### Lo que SIGUE pendiente antes de reactivar esto en `src/main`

**Bundlear el language server (JS puro, ~16MB) dentro del `.zip` del
plugin.** Hoy `AnsibleLanguageServerFactory` sigue apuntando a
`node_modules/@ansible/ansible-language-server/...` (una ruta de desarrollo,
relativa al working directory de `runIde`, que solo resuelve por casualidad
en el sandbox). Plan concreto para la proxima sesion que toque esto:

1. Agregar una `Copy` task de Gradle en `build.gradle.kts` que corra
   (o dependa de) `npm install --omit=dev` en un directorio de build, y
   copie `node_modules/@ansible/ansible-language-server/` completo (con su
   arbol de dependencias resuelto) a algo como
   `build/bundled-lsp/ansible-language-server/`.
2. Incluir ese directorio en el `.zip` final — el plugin gradle de IntelliJ
   permite agregar archivos sueltos al artifact via el bloque
   `intellijPlatform.pluginConfiguration` o directamente ajustando la tarea
   `buildPlugin`/`prepareSandbox` para copiar ese folder dentro de
   `lib/` o una carpeta hermana del jar (a diferenciar de "resource dentro
   del jar", que NO sirve aca porque Node necesita archivos reales en disco,
   no entries de un `.jar`).
3. En `AnsibleLanguageServerFactory`, resolver la ruta al `server.js`
   empaquetado relativa a la instalacion del plugin (ej.
   `PluginManagerCore.getPlugin(pluginId)?.pluginPath`), no a un
   `node_modules/` de desarrollo.
4. Confirmar con `verifyPlugin` que el .zip final trae esos archivos y que
   el tamaño total sigue siendo razonable (~16MB adicionales, nada parecido
   a los 500MB+ que hubiera costado bundlear Node mismo).

## Para reactivar (una vez resuelto el bundling de arriba)

1. Mover `detection/`, `lsp/`, y `runtime/` de vuelta a
   `src/main/kotlin/dev/gaphunter/ansiblecompanion/`, y `detection-tests/` +
   `runtime-tests/` a `src/test/kotlin/dev/gaphunter/ansiblecompanion/...`.
2. Devolver a `build.gradle.kts`: `bundledPlugin("org.jetbrains.plugins.yaml")`
   y `plugin("com.redhat.devtools.lsp4ij", "0.20.1")` (o version vigente),
   mas la `Copy` task del language server (ver arriba).
3. Devolver a `plugin.xml`: los `<depends>` de yaml/lsp4ij y los dos
   bloques `<extensions>` (fileTypeOverrider + server/fileTypeMapping).
4. Correr `verifyPlugin` completo (6 IDEs) antes de publicar — no solo
   `test`/`buildPlugin`.
