<p align="center">
    <img src="./src/main/resources/assets/createharmonics/textures/gui/logo_small.png" alt="Logo">
</p>
<h1 align="center"> Create: Harmonics <br><br>
    <a href="https://www.curseforge.com/minecraft/mc-mods/create-harmonics"><img alt="Curseforge Page" src="https://img.shields.io/badge/Harmonics-Curseforge?logo=curseforge&label=CurseForge&color=ff5277&style=for-the-badge"></a>
    <a href="https://modrinth.com/mod/create-harmonics"><img alt="Modrinth Page" src="https://img.shields.io/badge/Harmonics-Modrinth?logo=modrinth&label=Modrinth&color=%2300AF5C&style=for-the-badge"></a>
    <a href=""><img alt="Github Page" src="https://img.shields.io/badge/GitHub-Source_Code-blue?logo=github&style=for-the-badge"></a>
</h1>

<h4 align="center"> Required dependencies 
<br><br>
    <a href="https://www.curseforge.com/minecraft/mc-mods/kotlin-for-forge">
        <img alt="KotlinForForgeDependency" src="https://img.shields.io/badge/KOTLIN%20FOR%20FORGE-REQUIRED-7f52ff?logo=kotlin&labelColor=white&style=for-the-badge">
    </a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/create">
        <img alt="CreateModDependency" src="https://img.shields.io/badge/Create Mod-REQUIRED-ecaf89?logo=CurseForge&labelColor=white&style=for-the-badge">
    </a>
</h4>

<hr>

### Welcome to Create: Harmonics

This mod lets you play music from URLs in Minecraft, to listen with your friends and enhance any kind of builds you
make!

Everything is built to work with _Create_, so you have to use rotational power to make the music play, and make special
records, named **Ethereal Records**.

Playback is also fully compatible with contraptions, so you can have moving speakers, on elevators, trains, or anything
else you can think of!

### Features

*   You can play music from URLs, this mod supports a lot of sources, including **YouTube**, **YouTube Music**, **Soundcloud** and many more.
*   For the _Andesite Jukebox_, playback pitch depends on the input rotation speed, half the maximum speed plays at normal pitch
*   For adding music from URLs, you must use a Record Press Base
*   When you craft an Ethereal Record, and no url is provided, it will play the music from the music disc you used to craft it
*   Display links can show the currently playing song
*   Jukebox supports loop mode when redstone powered
*   Mechanical arms know when they can extract a record (when a song finishes) and insert a new one, for automated music systems
*   Each music disc type has some special audio effects in it
*   Pitch on contraption depends on contraption speed
*   Audio is synchronized between players in multiplayer
* **Valkyrien skies** support

### Planned features

*   A lot more record types with unique audio effects
*   Programmable Ethereal Record, with custom audio effects
*   More directly supported platforms
*   Brass jukebox, with configurable pitch, range and record collections
*   Speakers and audio routing, for more controlled audio systems

### How it works

This mod uses **ytdlp** for retrieving the stream link, then this link is sent to **ffmpeg** which handles
the transcoding in _PCM_ format and then the audio is sent after the processing chain to native minecraft channels

**It should be compatible natively with other audio handling mods since its using Minecraft directly for playing sounds,
and not accessing to OpenAL**

> [!IMPORTANT]
> NO AUDIO is being streamed server to client and never will; clients themselves stream the audio from the original
> source, as they're watching/playing it from their browsers.

### Important notice

Since this mod relies on ffmpeg, and ytdlp, it can download from **official** and trusted sources these binaries, asking
you for permission first.
If you don't want this, you can provide your own binaries by placing them in the correct folder (displayed on the mod if
you choose manual installation).
The code is completely open source, and transparency is important to me, since it's downloading and executing external
binaries on your computer.

> [!CAUTION]
> Only TRUST the official releases of this mod! This is very important. You can contact me personally on discord if you
> have any doubts about the authenticity of a release.

### Legal stuff

This mod is not affiliated with YouTube or any other music platform, and does not host any audio content.
Please respect the terms of service of the platforms you use to get audio from.
I do not condone unauthorized distribution of copyrighted material, nor I am responsible for any misuse of this mod.
This mod is provided as-is, without any warranties or guarantees of any kind.
By using this mod, you agree to use it responsibly and respect the rights of content creators.

## Credits

### Software
- ffmpeg: https://ffmpeg.org/
    - Windows builds by BtbN: Bundle licensed under _LGPLV2.1+_
    - Linux (static) builds by John Van Sickle: Bundle licensed under _GNU GPLV3+_
    - Mac builds by evermeet.cx: Bundle licensed under _LGPLV2.1+_
- ytdlp: https://github.com/yt-dlp/yt-dlp, licensed under _Unlicense_
- Create: https://github.com/Creators-of-Create/Create, licensed under _MIT License_

### Translations

- Japanese: @Yurisan001

Read the full license [here](LICENSE.MD)

### Support the development

If you want to support the development of this mod, you can do it by:

- Sharing the mod with your friends
- Reporting issues and suggesting features on the issue tracker and contributing code

<h4 align="center"> 
    Join the community and discover other amazing projects <br><br> 
    <a href="https://discord.gg/MM5QjqyqwC">
    <img alt="Discord icon" src="https://img.shields.io/discord/1076545114693972039?label=Discord&logo=discord&color=5865F2">
    </a>
</h4>



