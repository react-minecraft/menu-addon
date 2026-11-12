<div align="center">

<a href="https://git.io/typing-svg"><img src="https://readme-typing-svg.demolab.com?font=Manrope&weight=700&size=38&pause=1000&color=EB2544&background=FFFFFF00&center=true&vCenter=true&width=700&lines=Menu+Addon" alt="Menu Addon"/></a>

<p>Меню игроков и истории проверок React</p>

<p>
  <a href="https://react-ac.space"><img src="https://img.shields.io/badge/Website-EB2544?style=plastic&logo=googlechrome&logoColor=white" alt="Website"/></a>
  <a href="https://t.me/react_ac"><img src="https://img.shields.io/badge/Telegram-26A5E4?style=plastic&logo=telegram&logoColor=white" alt="Telegram"/></a>
  <a href="https://t.me/react_ac_support"><img src="https://img.shields.io/badge/Support-555555?style=plastic&logo=telegram&logoColor=white" alt="Support"/></a>
</p>

<p>
  <a href="https://github.com/react-minecraft/menu-addon/actions/workflows/build.yml"><img src="https://img.shields.io/github/actions/workflow/status/react-minecraft/menu-addon/build.yml?style=plastic&logo=github&logoColor=white&branch=master&label=build" alt="Build"/></a>
  <a href="https://github.com/react-minecraft/menu-addon/releases/tag/nightly"><img src="https://img.shields.io/badge/Nightly-EB2544?style=plastic&logo=github&logoColor=white" alt="Nightly"/></a>
  <a href="https://github.com/react-minecraft/menu-addon/blob/master/LICENSE"><img src="https://img.shields.io/github/license/react-minecraft/menu-addon?style=plastic&logo=opensourceinitiative&logoColor=white" alt="License"/></a>
</p>

</div>

## Возможности

Аддон открывает список игроков и историю их проверок. Шаблоны `templates/target-selector.yml` и `templates/selected-target.yml` задают внешний вид экранов.

| Команда | Право |
|---|---|
| `/react menu` | `react.menu.view` по умолчанию |
| `/react menu reload` | То же право |

Название подкоманды и право задаются в `settings.argument-literal` и `settings.permission`. Чтобы применить их изменение, перезапустите аддон. `settings.include-empty-history` добавляет в список игроков без истории. Параметры и сообщения находятся в `main-config.yml`.


## Установка

1. Возьмите `menu-addon-*.jar` со страницы [Nightly](https://github.com/react-minecraft/menu-addon/releases/tag/nightly) или соберите его из исходников.
2. Положите JAR в `plugins/React/addons/`. Нужен React с API 2.
3. Перезапустите сервер или выполните `/react reboot addons`. Настройки появятся в `plugins/React/addons/ReactMenuAddon/`.

React также может установить отсутствующий аддон из стабильного канала, когда доступен подписанный релиз для API 2.


## Сборка

Для сборки используйте JDK 21. Выполните `./gradlew build` или `.\gradlew.bat build` на Windows.

Готовый `menu-addon-*.jar` лежит в `common/build/libs/`.


## Лицензия

[MIT](LICENSE).
