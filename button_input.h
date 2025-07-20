#ifndef _BUTTON_INPUT_H_
#define _BUTTON_INPUT_H_

#include "mbed.h"

#define delay(t)        ThisThread::sleep_for(t)

typedef enum { NONE = 0, BTN1 = 1, BTN2 = 2} btn_input_t;
BusIn btnBus(PA_14, PB_7);

btn_input_t btnPressed(){
	static int prevState = 0xffff;
	int currState = btnBus;
	
	if (currState != prevState){
		delay(50ms);
		prevState = currState;
		return (btn_input_t(~currState & 0x03));
	}
	
	return NONE;
}

#endif