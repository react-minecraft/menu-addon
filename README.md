<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Menu+Addon" alt="Menu Addon Typing SVG"/></a>

<p>Меню истории проверок для React</p>

<p>
  <a href="https://github.com/react-minecraft/menu-addon/blob/master/LICENSE">
    <img src="https://img.shields.io/github/license/react-minecraft/menu-addon?style=flat&logo=opensourceinitiative&logoColor=white" alt="License">
  </a>
  <a href="https://github.com/react-minecraft/menu-addon/actions/workflows/build.yml">
    <img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/menu-addon/build.yml?branch=master&style=flat&logo=github" alt="Build">
  </a>
</p>

<p>
  <a href="https://react-ac.space">Сайт</a>
  &nbsp;•&nbsp;
  <a href="https://t.me/react_ac">Канал</a>
  &nbsp;•&nbsp;
  <a href="https://t.me/react_ac_support">Поддержка</a>
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
| `/react menu reload` | `react.menu.view` |

## Конфиг

```yaml
settings:
  argument-literal: menu        # после смены нужен рестарт
  permission: react.menu.view
  include-empty-history: false  # показывать игроков без истории
```

## Установка

1. Соберите аддон или скачайте jar из релизов.
2. Положите `reactmenuaddon-common-<версия>.jar` в `plugins/React/addons/`.
3. Перезапустите сервер. Конфиг появится в папке аддона.

## Сборка

```bash
./gradlew build
```

Готовый jar лежит в `common/build/libs/`.

## Лицензия

См. [LICENSE](LICENSE).
