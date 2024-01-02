## Automated Minecraft Player

### Features
- Records user sessions playing the game to disk
  - Start and stop recording with hotkeys
- Chooses random recording and plays the game
- Stop recording via written chat commands or hotkeys
- Pauses on server stop, staff member nearby or another player nearby for too long
- Reads inventory and sells items at will to progress in server
- Hotkey to speed up Minecraft's built in ctrl + click stack movement
- Hotkey to automatically organize inventory with preset layouts
  - Interface for managing and switching layouts added to Minecraft's inventory UI
- Websocket connection to discord bot to allow remote control from any device
- Show and hide custom features for screen recording

### Preview of Automated Gameplay
[![Main Preview](http://img.youtube.com/vi/XdQ_zjCe4Z4/0.jpg)](https://youtu.be/XdQ_zjCe4Z4)


### Technologies Used
- Java + Typescript for external API/WS connection
- Minecraft Forge API
- Discord API
- Custom API https://isadore.co
- Custom websockets https://isadore.co
- Custom pixel graphics
- Individual user keys for API/WS security
