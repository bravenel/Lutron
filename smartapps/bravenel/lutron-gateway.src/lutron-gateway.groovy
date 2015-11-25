/**
 *  Lutron Gateway
 *
 *  Copyright 2015 Bruce Ravenel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
definition(
    name: "Lutron Gateway",
    namespace: "bravenel",
    author: "Bruce Ravenel",
    description: "Create virtual devices for each Lutron device and control Lutron with them",
    category: "Convenience",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png")

preferences {
	page(name: "selectLutron")
}

def getCmd(myCmd, n) {
	def result = input myCmd, "text", title: "Lutron Command #$n", required: true
}

def getName(myName, n) {
	def result = input myName, "text", title: "Device Name #$n", required: true
}

def selectLutron() {
	dynamicPage(name: "selectLutron", title: "Lutron Gateway and Devices", uninstall: true, install: true) {
		section("") {
			input "Lutron", "capability.musicPlayer", title: "Lutron gateway", required: true, multiple: false
			input "Remove", "capability.momentary", title: "Remove button", required: true, multiple: false
			input "howMany", "number", title: "How many Lutron devices?", required: true, submitOnChange: true
		}
		section("Select Lutron Devices") {
			for (int i = 0; i < howMany; i++) {
				def thisCmd = "dCmd$i"
				def thisName = "dName$i"
				getCmd(thisCmd, i + 1)
				getName(thisName, i + 1)
                paragraph(" ")
			}
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	unsubscribe()
	initialize()
}

def initialize() {
	state.myDevices = [:]
    state.myPhantoms = [:]
	for (int i = 0 ; i < howMany; i++) {
        def thisCmd = settings.find {it.key == "dCmd$i"}
        def thisName = settings.find {it.key == "dName$i"}
    	def deviceId = "Lutron" + "$i-" + app.id
        def myDevice = getChildDevice(deviceId)
        if(!myDevice) myDevice = addChildDevice("smartthings", thisCmd.value[1] in ['d', 'D'] ? "Virtual Dimmer" : "On/Off Button Tile", deviceId, null, [label: thisName.value])
        myDevice.name = thisCmd.value
        myDevice.displayName = thisName.value
        if(myDevice.name[0] in ['s', 'S']) {        //  SSI or SDL, build map based on Lutron zone #
        	def iloc = myDevice.name.indexOf(',') + 1
			def ndx = myDevice.name.substring(iloc).toInteger()
			state.myDevices << ["$ndx":deviceId]
        } else if(myDevice.name[0] in ['b', 'B']) { // BP, build map based on Phantom button #
        	def iloc = myDevice.name.indexOf(',') + 1
            def ndx = myDevice.name.substring(iloc).toInteger()
            state.myPhantoms << ["$ndx":deviceId]
        }
        if(myDevice.name[1] in ['d', 'D']) {		//  SDL, dimmer
        	subscribe(myDevice, "level", dimmerHandler)
			subscribe(myDevice, "switch.on", dimmerHandler)
			subscribe(myDevice, "switch.off", dimmerHandler)
        } else {
        	subscribe(myDevice, "switch.on", switchHandler)
        	subscribe(myDevice, "switch.off", switchHandler)
        }
    }
    subscribe(Lutron, "msgRcvd", LutronHandler)
    subscribe(Remove, "switch.on", removeHandler)
    state.zmp = ",00000000000000000000000000000000"
    state.lmp = ",000000000000000"
}

def switchHandler(evt) {
	def myDev = evt.device
	Lutron.sendMsg("$myDev.name,$evt.value")
}

def dimmerHandler(evt) {
	def myDev = evt.device
    def level = evt.value
    if(level == "on") level = myDev.currentLevel ?: 100
    else if(level == "off") level = 0
	Lutron.sendMsg("$myDev.name,$level")
}

// In this Lutron message handler, we become aware of state changes from Lutron
// We send events to ST to cause the virtual switches to turn on/off to reflect the change
// But, we don't want to send new on/off messages to Lutron as a result
// So, we use "son" for "on", and "soff" for "off", to indicate this desire
// Modified device types for On/Off Button Tile and Virtual Dimmer handle "son" and "soff" as if they were on/off
// This code explicitly subscribes to "on"/"off" so as to avoid "son"/"soff", so no message is sent to Lutron
 
def LutronHandler(evt) {
	if (evt.value.startsWith("LZC") && !evt.value.endsWith("CHG")) {  // process on/off of zone
		def ndx = evt.value.substring(evt.value[4] == "0" ? 5 : 4, 6)
		def device = getChildDevice(state.myDevices["$ndx"])
		device.sendEvent(name: "switch", value: evt.value.endsWith("ON ") ? "son" : "soff")
	} else if (evt.value.startsWith("ZMP")) {			  // process zmp report to catch zone on/off
		def zmp = evt.value.substring(3)
		for (int i = 1; i <= state.myDevices.size(); i++) if(state.zmp[i] != zmp[i]) { // state changed
			def device = getChildDevice(state.myDevices["$i"])
			device.sendEvent(name: "switch", value: zmp[i] == "1" ? "son" : "soff")
		}
		state.zmp = zmp
	} else if (evt.value.startsWith("LMP")) { 			  // process lmp report to catch BP off
    	def lmp = evt.value.substring(3)
        for (int i = 1; i <= state.myPhantoms.size(); i++) if((state.lmp[i] == '1') && (lmp[i] == '0')) {  // state changed to off
        	def device = getChildDevice(state.myPhantoms["$i"])
            device.sendEvent(name: "switch", value: "soff")
        }
        state.lmp = lmp
    }
}

def removeHandler(evt) {
	unsubscribe()
}

def uninstalled() {
	removeChildDevices(getChildDevices())
}

private removeChildDevices(delete) {
	delete.each {deleteChildDevice(it.deviceNetworkId)}
}