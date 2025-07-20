#include "motordriver.h"

#define delay(t)        ThisThread::sleep_for(t)

Motor::Motor(PinName pwm, PinName dir) 
: _pwmObj(pwm), _dirObj(dir)
{
	_sign = FORWARD_DIR;
	_dirObj = _sign;
	
	_speed = 0;
	_pwmObj = _speed;
}
Motor::~Motor() {}
void Motor::forward(double speed){
	if (_sign == FORWARD_DIR && _speed == speed)
		return;
	if (_sign == BACKWARD_DIR) {
		_pwmObj = 0;		// stop
		delay(25ms);
	}
	
	_sign = FORWARD_DIR;
	_dirObj = _sign;
	_speed = speed;
	_pwmObj = _speed;
}
void Motor::backward(double speed) {
	if (_sign == BACKWARD_DIR && _speed == speed)
		return;
	if (_sign == FORWARD_DIR) {
		_pwmObj = 0;		// stop
		delay(25ms);
	}
	
	_sign = BACKWARD_DIR;
	_dirObj = _sign;
	_speed = speed;
	_pwmObj = _speed;
}
void Motor::stop() {
	_speed = 0;
	_pwmObj = _speed;
}