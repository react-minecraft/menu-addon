<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Menu+Addon" alt="Menu Addon Typing SVG"/></a>

<p>Меню истории проверок для React</p>

<p>
  <a href="https://github.com/react-minecraft/menu-addon/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/react-minecraft/menu-addon?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
  <a href="https://github.com/react-minecraft/menu-addon/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/menu-addon/build.yml?branch=master&style=plastic&logo=github&logoColor=white" alt="Build">
  </a>
</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

</div>

---

## Что это?

- Аддон для плагина [React](https://github.com/react-minecraft/plugin): открывает меню с игроками и историей их
  проверок.
- Внешний вид меню задаётся шаблонами в `templates/` (`target-selector.yml`, `selected-target.yml`).
- Сообщения — списки действий (`[msg]`, `[bar]`, `[sound]`) с MiniMessage.

## Команды

| Команда | Право |
|---|---|
| `/react menu` | `react.menu.view` (меняется в `settings.permission`) |
| `/react menu reload` | То же право, что у `/react menu` |

## Конфиг

```yaml
settings:
  argument-literal: menu        # после смены перезапустите аддон
  permission: react.menu.view
  include-empty-history: false  # показывать игроков без истории
```

## Установка

1. Соберите аддон или скачайте jar из [GitHub Nightly](https://github.com/react-minecraft/menu-addon/releases/tag/nightly). Нужен React с API 2.
2. Положите `menu-addon-<версия>.jar` в `plugins/React/addons/`.
3. Перезапустите сервер или выполните `/react reboot addons`. Конфиг появится в `plugins/React/addons/ReactMenuAddon/`.

## Сборка

Для сборки нужен JDK 21.

```bash
./gradlew build
```

Готовый jar лежит в `common/build/libs/`.

## Лицензия

См. [LICENSE](LICENSE).
