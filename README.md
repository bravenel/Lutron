# Lutron RadioRA 
SmartThings Lutron RadioRa Integration

There are three Device Types that you need to install in the IDE -
  Lutron Shield, 
  On/Off Button Tile, 
  Virtual Dimmer.
  
These need to be in your "My Device Types"

There is the Arduino sketch, that installs on Arduino Mega 2560. 

There is the Lutron Gateway SmartApp, that you install in the IDE.

The required hardware consists of -
  Lutron RadioRA RA-RS232, 
  Arduino Mega 2560, with, 
  SmartThings ThingShield, and
  Arduino RS232 shield.
  
  The Arduino RS232 shield connects to the RA-232 with null modem cable.
  There must be jumpers from pins 2,3,4,5 to pins 14,15,16,17 on the Mega.
