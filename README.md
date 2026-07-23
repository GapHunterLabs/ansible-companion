# Ansible Companion

Plugin de IntelliJ/PyCharm. **v0.1.0: encriptar y desencriptar Ansible
Vault (formato 1.1/AES256) directo en el editor**, sin depender de
`ansible-vault` instalado.

## Por qué existe

Nace de evidencia real de reseñas del JetBrains Marketplace, no de
suposiciones: los incumbentes de este nicho (pago y gratis) tienen quejas
concretas y repetidas sobre precio, YAML que no debería tocar, y jinja2
mal parseado. El vault de/encriptado en particular es una de las
funciones que los usuarios más valoran del incumbente pago — y una de las
pocas piezas que se puede construir sin depender de un language server
externo.

## Por qué reimplementado, no `ansible-vault` por línea de comandos

Ansible **no corre en Windows nativo** (`ansible-core` falla al importar
`fcntl`/`os.get_blocking`, ambos POSIX-only) — y buena parte de los
usuarios reales de este plugin están en Windows justo porque por eso
necesitan IntelliJ/PyCharm para editar Ansible, no una VM Linux con vim.
Pedirles que instalen `ansible-vault` como prerrequisito no es una salida.

`AnsibleVaultCipher` reimplementa el formato 1.1/AES256 con
`javax.crypto` del propio JDK — cero dependencias nuevas. Verificado
contra el vector de prueba real del test suite de `ansible/ansible`
(`test/units/parsing/vault/test_vault.py`) y contra un test de plataforma
real (`BasePlatformTestCase`) que confirma el round-trip
encriptar→desencriptar sobre un editor de verdad.

## Uso

![Encriptar y desencriptar un valor de Ansible Vault directo en el editor](docs/vault-demo.gif)

Seleccioná texto en el editor → clic derecho:
- **Encrypt Selection as Ansible Vault** — pide un password, reemplaza la
  selección con un bloque `$ANSIBLE_VAULT;1.1;AES256`.
- **Decrypt Ansible Vault Selection** — sobre un bloque ya encriptado,
  pide el password y lo reemplaza con el texto plano.

El password nunca se guarda ni se cachea entre usos.

## Qué viene después (`future/v0.2-ansible-lsp/`)

Completado consciente de FQCN (`ansible.builtin.*`), parseo correcto de
Jinja2 dentro de YAML, y detección de tipo de archivo que no secuestra
YAML de Kubernetes/Helm/Docker-compose — construido y con tests, pero
apartado de esta release. Bloqueador real: empaquetar Node.js +
`@ansible/ansible-language-server` dentro del `.zip` del plugin (hoy
asume Node instalado en el sistema, lo que no es aceptable para
distribución). Ver `future/v0.2-ansible-lsp/README.md`.

Soporte de roles y preview de variables multi-entorno todavía no están
construidos.

## Desarrollo

```
./gradlew test           # tests unitarios + de plataforma
./gradlew buildPlugin     # genera build/distributions/*.zip
./gradlew verifyPlugin    # chequea compatibilidad contra IDEs reales
```

`runIde` (levanta un IntelliJ completo) queda reservado para verificación
puntual, no para el loop de todos los días — para probar acciones de
editor, un test de plataforma (`BasePlatformTestCase`, ver
`src/test/kotlin/.../vault/VaultEditorOpsTest.kt`) es más rápido y no
depende de clicks de mouse.

## Licencia

Apache-2.0. Ver `LICENSE`.
