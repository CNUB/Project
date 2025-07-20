#include "mbed.h"
#include "pw_mode.h"
#include "Adafruit_SSD1306.h"
#include <chrono>

using namespace std::chrono;

extern Adafruit_SSD1306_I2c myOled;
extern AnalogIn xAxis;
extern AnalogIn yAxis;

static int pw[4] = {0,0,0,0};
static int ppw[4] = {-1,-1,-1,-1};
int rpw[4] = {1,2,3,4};

static int xp[4] = {1, 32, 64, 96};
static int yp = 25;

void displayPassword() {
	bool bRefreshed = false;
	myOled.setTextSize(4);
	for (int i = 0; i < 4; i++) {
		if (pw[i] != ppw[i]) {
			myOled.setTextCursor(xp[i], yp);
			myOled.writeChar(0x30 + pw[i]);
			ppw[i] = pw[i];
			bRefreshed = true;
		}
	}
	
	if (bRefreshed) {
		myOled.display();
	}
}

static int currCursorPos = 0;
static int prevCursorPos = -1;

#define CURSOR_Y_POS		(yp + 29)
#define CURSOR_WIDTH		26
#define CURSOR_HEIGHT		2

void displayCursor() {
	if (currCursorPos != prevCursorPos) {
		// 이전 커서 지우기
		if(prevCursorPos >= 0) {
			myOled.fillRect(xp[prevCursorPos], CURSOR_Y_POS, CURSOR_WIDTH, CURSOR_HEIGHT, 0);
		}
		
		// 새 커서 그리기 (숫자 바로 아래)
		myOled.fillRect(xp[currCursorPos], CURSOR_Y_POS, CURSOR_WIDTH, CURSOR_HEIGHT, 1);
		myOled.display();
		
		prevCursorPos = currCursorPos;
		
		// 디버깅: 커서 위치 정보 출력
		printf("Cursor drawn at: X=%d, Y=%d (pos=%d)\n", 
			   xp[currCursorPos], CURSOR_Y_POS, currCursorPos);
	}
}

typedef enum { NEUTRAL=0, LEFT=1, RIGHT=-1, UP=1, DOWN=-1} js_value_t;
static volatile js_value_t xv = NEUTRAL, yv = NEUTRAL;

// 조이스틱 연결 테스트 함수
void testJoystick() {
	printf("=== Joystick Connection Test ===\n");
	for(int i = 0; i < 5; i++) {
		float x_raw = xAxis.read();
		float y_raw = yAxis.read();
		int x = int(x_raw * 100);
		int y = int(y_raw * 100);
		
		printf("Test %d: X=%.3f(%d), Y=%.3f(%d)\n", 
			   i+1, x_raw, x, y_raw, y);
		ThisThread::sleep_for(300ms);
	}
	printf("=== Test Complete ===\n");
}

// 간소화된 조이스틱 처리
void getJoysticValue() {
	static auto last_read = Kernel::Clock::now();
	auto current_time = Kernel::Clock::now();
	
	// 150ms마다 읽기 (안정적인 응답속도)
	if(duration_cast<milliseconds>(current_time - last_read).count() < 150) return;
	last_read = current_time;
	
	float x_raw = xAxis.read();
	float y_raw = yAxis.read();
	int x = int(x_raw * 100);
	int y = int(y_raw * 100);
	
	// 디버깅: 조이스틱 원시 값 확인 (가끔씩만)
	static int debug_count = 0;
	if(debug_count++ % 15 == 0) { // 약 2.25초마다 출력
		printf("JS Raw: X=%.2f(%d), Y=%.2f(%d)\n", x_raw, x, y_raw, y);
	}
	
	// 조이스틱 값 기준 설정
	// 일반적인 조이스틱: 중앙=50, 범위=0~100
	int center = 50;
	int threshold = 25; // 임계값 (데드존)
	
	// X축 처리 (좌우)
	if (x > center + threshold) {
		xv = RIGHT;
	} else if (x < center - threshold) {
		xv = LEFT;
	} else {
		xv = NEUTRAL;
	}
	
	// Y축 처리 (상하) 
	if (y > center + threshold) {
		yv = DOWN;
	} else if (y < center - threshold) {
		yv = UP;
	} else {
		yv = NEUTRAL;
	}
	
	// 디버깅: 조이스틱 상태 변화만 출력
	static js_value_t prev_xv = NEUTRAL, prev_yv = NEUTRAL;
	if(xv != prev_xv || yv != prev_yv) {
		printf(">>> JS State: X=%s, Y=%s (raw: %d,%d)\n", 
			   xv==LEFT?"LEFT":(xv==RIGHT?"RIGHT":"CENTER"),
			   yv==UP?"UP":(yv==DOWN?"DOWN":"CENTER"),
			   x, y);
		prev_xv = xv;
		prev_yv = yv;
	}
}

void changePWandCursor() {
	static auto last_change = Kernel::Clock::now();
	auto current_time = Kernel::Clock::now();
	
	// 500ms마다 변경 (너무 빠르면 조작하기 어려움)
	if(duration_cast<milliseconds>(current_time - last_change).count() < 500) return;
	
	bool changed = false;
	
	// 세로 방향: 숫자 변경
	if (yv != NEUTRAL) {
		int num = pw[currCursorPos];
		if (yv == UP) {
			if (++num > 9) num = 0;
			printf("▲ Number UP: pos=%d, value=%d\n", currCursorPos, num);
		}
		else if (yv == DOWN) {
			if (--num < 0) num = 9;
			printf("▼ Number DOWN: pos=%d, value=%d\n", currCursorPos, num);
		}
		pw[currCursorPos] = num;
		changed = true;
	}
	// 가로 방향: 커서 이동
	else if (xv != NEUTRAL) {
		prevCursorPos = currCursorPos;
		if (xv == LEFT) {
			if (--currCursorPos < 0) currCursorPos = 3;
			printf("◀ Cursor LEFT: pos=%d\n", currCursorPos);
		}
		else if (xv == RIGHT) {
			if (++currCursorPos > 3) currCursorPos = 0;
			printf("▶ Cursor RIGHT: pos=%d\n", currCursorPos);
		}
		changed = true;
	}
	
	if(changed) {
		last_change = current_time;
		// 현재 비밀번호 상태 출력
		printf("Password: [%d][%d][%d][%d] - Cursor at pos %d\n", 
			   pw[0], pw[1], pw[2], pw[3], currCursorPos);
	}
}

static DigitalIn enterBtn(PB_7);
bool isPWRight;

void isRight() {
	for (int i = 0; i < 4; i++) {
		if (pw[i] != rpw[i]) {
			isPWRight = false;
			return;
		}
	}
	isPWRight = true;
}

bool changePW() {
	isRight();
	if(isPWRight) {
		printf("*** PASSWORD CORRECT! ***\n");
	} else {
		printf("Password incorrect. Try: [%d][%d][%d][%d]\n", 
			   rpw[0], rpw[1], rpw[2], rpw[3]);
	}
	return isPWRight;
}

void initPwMode() {
	myOled.setTextSize(4);
	for (int i = 0; i < 4; i++) {
		pw[i] = 0;
		ppw[i] = -1;
	}
	currCursorPos = 0;
	prevCursorPos = -1;
	isPWRight = false;
	
	printf("=== Password Mode Initialized ===\n");
	printf("Correct Password: [%d][%d][%d][%d]\n", rpw[0], rpw[1], rpw[2], rpw[3]);
	printf("Use Joystick: UP/DOWN = change number, LEFT/RIGHT = move cursor\n");
	
	// 조이스틱 테스트 실행
	testJoystick();
}

void cleanupPwMode() {
	printf("=== Password Mode Cleaned Up ===\n");
}

void doPwModeOperation() {
	// 조이스틱 값 업데이트 (메인 루프에서 수행)
	getJoysticValue();
	changePWandCursor();
	
	displayPassword();
	displayCursor();
}