# VR Desktop

* Are you using multiple monitors or large monitor?
* Have you ever wanted to bring your monitor(s) in a small backpack?
* Did you want operate with a large 4K monitor without taking up a lot of space?

VR Desktop's main goal is to migrate your physical monitors/desktop into VR. With a Cardboard or a more comfortable VR headset you can have as big monitor as you want. Some vendors already prvide such functionality (like Oculus has one), but I want to provide an independent solution which works generally with Android. Your operating system also doesn't matter (explained later).

Tools needed:
1. VR capable Android phone.
2. Desktop environment with an RDP connection.

Advised toolset:
1. At least 1080p screen resolution Android phone.
2. Direct USB connection to decrease latency and increase throughput.
3. VirtualBox VM machine. Purpose of using VirtualBox is two fold: it provides RDP connection out of the box, and with the VM guest additions you can configure a 4K monitor without owning one.
4. Use adb reverse proxy functionality to project the VirtualBox's RDP port onto your phone, so you can connect to that port directly.

The advised toolset provides OS independent way of configuring any size monitor for your VR desktop.

Technology: VR desktop is basically an aFreeRDP client with a custom cardboard UI front-end. By relying on VirtualBox we can reuse the hard work of Oracle's team by enabling a platform independent way of providing physycally non existent monitors in the system.

TODO:
* Come up with the MVP (finish integrating the cardboard viewer into aFreeRDP)
* Make the virtual monitor configurable (distance, height/width)
* Make the virtual monitor bendable
* Allow multiple monitors

Challenges:
* Hardware acceleration in the VirtualBox environment
* Latency and throughput
