
NoCheatPlus
---------
[![Build Status](https://ci.codemc.io/buildStatus/icon?job=Updated-NoCheatPlus%2FUpdated-NoCheatPlus)](https://ci.codemc.io/job/Updated-NoCheatPlus/job/Updated-NoCheatPlus/)
[![Discord](https://img.shields.io/discord/598285007496151098?label=discord&logo=discord)](https://discord.gg/NASKHYc)

Updated-NoCheatPlus is a continuation of the famous anti-cheat plugin NoCheatPlus, introduced by [NeatMonster](https://github.com/NeatMonster) and [Asofold](https://github.com/asofold) building on the code base of [NoCheat](https://github.com/md-5/NoCheat), created by [Evenprime](https://github.com/Evenprime).

NoCheatPlus attempts to enforce "vanilla Minecraft" mechanics, as well as preventing players from abusing weaknesses in Minecraft or its protocol, making your server more safe. Organized in different sections, various checks are performed to test players doing, covering a wide range including flying and speeding, fighting hacks, fast block breaking and nukers, inventory hacks, chat spam and other types of malicious behaviour.

Installation
---------
* [Install a Spigot server](https://github.com/Updated-NoCheatPlus/NoCheatPlus/#obtain-a-build-of-spigot)
* [Download NoCheatPlus](https://github.com/Updated-NoCheatPlus/NoCheatPlus/#download)
* Drop the NoCheatPlus.jar in to the plugins folder.
* Start your Spigot/CraftBukkit server. (Using /reload can have unwanted side effects with players still online, but also with complex plugins and cross-plugin dependencies, so we don't recommend it. Usually it should work with NCP.)

Hints
---------
* Be sure that your Spigot/CraftBukkit and NoCheatPlus versions match together. The latest version of NCP is compatible with a wide range of CraftBukkit/Spigot versions.
* Don't use tabs in the config.yml file.
* Use [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) for full efficiency of the fight checks and other. Using a version of ProtocolLib that is supported by NCP is essential, as otherwise some checks will be disabled.
* For compatibility with other plugins such as mcMMO, citizens and more check out [CompatNoCheatPlus](https://github.com/Updated-NoCheatPlus/CompatNoCheatPlus).

Links
---------

###### Download
* [Jenkins (current)](https://ci.codemc.io/job/Updated-NoCheatPlus/job/Updated-NoCheatPlus/)
* [BukkitDev (legacy)](https://dev.bukkit.org/projects/nocheatplus/files/)
* [SpigotMC (legacy)](https://www.spigotmc.org/resources/nocheatplus2015-07-25.26/updates)
* [Jenkins (legacy)](https://ci.md-5.net/job/NoCheatPlus/)

###### Support and Documentation
* [Issues/Tickets](https://github.com/Updated-NoCheatPlus/NoCheatPlus/issues)
* [Wiki](https://github.com/Updated-NoCheatPlus/Docs)
* [Compatibility debugging guide](docs/compat-debugging.md)
* [Configuration](https://github.com/Updated-NoCheatPlus/Docs#configuration)
* [Permissions](https://github.com/Updated-NoCheatPlus/Docs/blob/master/Settings/Permissions.md)
* [Commands](https://github.com/Updated-NoCheatPlus/Docs/blob/master/Settings/Commands.md)

###### Developers
* [License (GPLv3)](https://github.com/Updated-NoCheatPlus/NoCheatPlus/blob/master/LICENSE.txt)
* [API](https://github.com/Updated-NoCheatPlus/Docs/blob/master/Development/API.md)
* [Contribute](https://github.com/Updated-NoCheatPlus/NoCheatPlus/blob/master/CONTRIBUTING.md)

###### Related Plugins
* [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/)
* [CompatNoCheatPlus](https://dev.bukkit.org/projects/compatnocheatplus-cncp/)

###### Obtain a build of Spigot
* [Get the latest BuildTools.jar](https://hub.spigotmc.org/jenkins/job/BuildTools/)
* [Run according to instructions](https://www.spigotmc.org/wiki/buildtools/)
* ([Server installation instructions](https://www.spigotmc.org/wiki/spigot-installation/))

Compiling NoCheatPlus
---------
* We use [Maven](http://maven.apache.org/download.cgi) 3 to handle the dependencies. Tested both with Eclipse and Jenkins is Maven 3.3.9.
* You can compile with this Maven goal: `mvn clean package`, for a build without any of the "non free" modules, which depened on not publicly downloadable resources, such as the CraftBukkit/Spigot server jar - the reflection based compatibility module is still contained. 
* To also (re-) build "non free" compatibility modules, use `-P nonfree_build` as well as activating the appropriate module to build via a profile such as `-P cbdev` - see the tables below for reference.
* For a build with full compatibility modules, You can compile with this goal: `mvn clean package -P nonfree_build -P all`.
* "Non free" jar file dependencies needed for the dedicated compat modules, which your local maven repository might be missing, can be installed manually.
Example for Eclipse with embedded maven:
Add a new maven build run configuration, name it appropriately, e.g. ```Install CB 1.7.5```.
Set goals to: ```install:install-file -Dfile=<PATH TO JAR> -DgroupId=org.bukkit -DartifactId=craftbukkit -Dversion=1.7.5-R0.1-SNAPSHOT -Dpackaging=jar```
On Windows the <PATH TO JAR> might look like:  ```X:\...\craftbukkit\3042\craftbukkit-1.7.5-R0.1-20140408.020329-16.jar```
To let it run you might have to set the base directory, e.g. to ```${workspace_loc}```, it does not seem to have significance.
Do set the correct version alongside the file name. On newer version of maven, you might do with much simplified goals, because the pom files inside the jars are parsed.
  * **The latest versions of BuildTools.jar will automatically install the created server jars into the local .m2 repository (e.g. on linux) - provided configuration paths are standard. Thus you don't need to do this manually anymore, if you then build NCP with the specific profile, if you have run BuildTools.jar to generate the server jars on that machine/environment.**

Options and profiles related to enabling/disabling including/building "non free" compatibility modules.

| Profile | Parameter | Description |
| :------------------ | :-------------- | :-------------- |
| `-P nonfree_build` | _none_ | Enable building "non free" compatibility modules. |
| _none_ | _none_ | The "non free" modules won't be included. |

Profiles for choice of "non free" compatibility modules to build:

| Profile | Description |
| :------------------ | :-------------- |
| _none_ | Default build without any of the native access modules, might pose compatibility issues with legacy Minecraft versions. The reflection based module is included here. |
| `-P all` | All compatibility modules. |
| `-P cbdev` | Spigot 1.12 R1 (MC 1.12-1.12.2). |
| `-P spigot1_11_r1` | Spigot 1.11 R1 (MC 1.11-1.11.2). |
| `-P spigot1_10_r1` | Spigot 1.10 R1 (MC 1.10-1.10.2). |
| `-P spigot1_9_r2` | Spigot 1.9 R2 (MC 1.9.4). |
| `-P spigot1_8_r3` | Spigot 1.8 R3 (MC 1.8.4-1.8.8). |
| `-P spigot1_7_r4` | Spigot 1.7 R4 (MC 1.7.10). |
| `-P cblegacy` | The pre-DMCA CraftBukkit builds. |
