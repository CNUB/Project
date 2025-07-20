#ifndef _MOTORDRIBER_H_
#define _MOTORDRIBER_H_

#include "mbed.h"

#define FORWARD_DIR		0
#define BACKWARD_DIR	1

class Motor {
	public:
		Motor(PinName pwm, PinName dir);
		~Motor();
		void forward(double speed);
		void backward(double speed);
		void stop();
	
	private:
		PwmOut 				_pwmObj;
		DigitalOut 		_dirObj;
		double 				_speed;
		int 					_sign;
	
};

#endif	//	_MOTORDRIBER_H_