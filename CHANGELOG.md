<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Ansible Companion Changelog

## [Unreleased]

## [0.1.2]

### Fixed

- La descripción del plugin y las tooltips de las dos acciones (en
  `plugin.xml`) seguían hardcodeadas en español, el mismo problema que se
  arregló en 0.1.1 para los diálogos — ahora en inglés, consistente con el
  resto del listing.

## [0.1.1]

### Fixed

- Los diálogos y mensajes de encriptar/desencriptar tenían texto hardcodeado en
  español, inconsistente con el resto de la UI del plugin (en inglés). Ahora
  todo el texto visible al usuario está en inglés.

## [0.1.0]

### Added

- Encriptar/desencriptar Ansible Vault (1.1/AES256) sobre la selección del editor,
  vía el menú contextual. Implementación propia con `javax.crypto` (sin `ansible-vault`
  instalado, sin dependencias nuevas) — verificada contra el vector de prueba real de
  `ansible/ansible` (`test/units/parsing/vault/test_vault.py`).
- `sinceBuild=243`, `untilBuild` abierto — evita la muerte por untilBuild estrecho.

### En pausa para 0.2.0

Ver `future/v0.2-ansible-lsp/README.md`. Bloqueador real: empaquetar Node +
`ansible-language-server` dentro del plugin (Ansible no corre en Windows nativo,
así que pedirle al usuario que lo instale no es una salida).

- Completado consciente de FQCN (`ansible.builtin.*`).
- Parseo correcto de Jinja2 dentro de YAML.
- Detección de tipo de archivo que no secuestra YAML de Kubernetes/Helm/Docker-compose.
- Soporte de roles, preview de variables multi-entorno.
