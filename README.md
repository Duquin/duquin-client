# Duquin Client

Duquin Client — бесплатный utility-мод для Minecraft **1.21.4** на Fabric.

![icon](icon.png)

## Возможности

- **Combat** — AttackAura, CrystalAura, TriggerBot, AntiCheatBypass, AutoTotem и др.
- **Movement** — Speed, Blink, FreeCam, Phase, TargetStrafe и др.
- **Render** — ClickGUI с красной темой, ESP, NameTags, BaseFinder, JumpCircles, Trails
- **Player** — ChestStealer, AutoPotion, InvseeExploit и др.
- **Misc** — Discord Rich Presence, Optimizer, IRC

Клиент включает собственный лаунчер (`tools/loader/`) на чистом WinAPI: сам ставит Fabric, скачивает Java при необходимости и запускает игру.

## Установка (для игроков)

### Вариант 1 — Лаунчер
1. Скачай `DuquinLoader.exe` из [Releases](../../releases)
2. Запусти — лаунчер всё сделает сам

### Вариант 2 — Вручную
1. Установи [Fabric Loader](https://fabricmc.net/use/installer/) для Minecraft 1.21.4
2. Скачай [Fabric API](https://modrinth.com/mod/fabric-api) и положи в `.minecraft/mods/`
3. Скачай `duquin-x.x.x.jar` из [Releases](../../releases) и положи в `.minecraft/mods/`
4. Запусти Minecraft с профилем Fabric

**Открыть меню читов:** `Right Shift`

## Сборка из исходников

Требуется JDK 21+.

```bash
./gradlew build
```

Готовый jar появится в `build/libs/`.

## Лицензия

[MIT](LICENSE) — код свободен для использования, модификации и распространения.


## Дисклеймер

Проект создан в образовательных целях. Использование на серверах может нарушать их правила — администрация сервера вправе забанить вас за использование сторонних модификаций.
