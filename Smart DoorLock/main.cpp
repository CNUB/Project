#include "mbed.h"
#include "Adafruit_SSD1306.h"
#include "motordriver.h"
#include "DHT.h"
#include "button_input.h"
#include "pw_mode.h"
#include <ctype.h>

#define delay(t)        ThisThread::sleep_for(t)

using namespace std::chrono;

enum { CLOSED, OPENED, CLOSING, OPENING } state;

#define GREEN_LED_PIN            PA_13
#define YELLOW_LED_PIN         PB_10
#define RED_LED_PIN               PA_4
BusOut ledBus(RED_LED_PIN, YELLOW_LED_PIN, GREEN_LED_PIN);

Motor motorA(PA_7, PC_8);

#define RUN_WAIT_TIME            5
#define FORCE_WAIT_TIME         30
Ticker waitTimer;
int wait_time = 0;
void waitTimerHandler(){
   wait_time --;
}

Ticker jsTicker;
int count;
#define BUZZER_PIN               PC_9
PwmOut buzzer(BUZZER_PIN);
int opening_melody[] = {
   3830, 3038, 2550, 3830      //   do mi sol do
};
int closing_melody[] = {
   3830, 2550, 3038, 3830      //    do sol mi do
};
void playMelody(int tone){
   buzzer.period_us(tone);
   buzzer = 0.5;
}

DHT dht22(PB_2, DHT22);
Ticker dht22Ticker;
float temp = 25.0f, humidity = 50.0f;
bool dht_read_flag = false;

void setDHTReadFlag(){
   dht_read_flag = true;
}

void readTempHumi(){
   if(dht_read_flag) {
      dht22.readData();
      humidity = dht22.ReadHumidity();
      temp = dht22.ReadTemperature(CELCIUS);
      dht_read_flag = false;
   }
}

// an I2C sub-class that provides a constructed default
class I2CPreInit : public I2C
{
public:
    I2CPreInit(PinName sda, PinName scl) : I2C(sda, scl)
    {
        frequency(400000);
    };
};

I2CPreInit i2cMaster(I2C_SDA, I2C_SCL);
Adafruit_SSD1306_I2c myOled(i2cMaster, D13, 0x78, 64, 128);

// 조이스틱 핀 (전역으로 선언)
AnalogIn xAxis(PC_2);
AnalogIn yAxis(PC_3);

#define BT_TX      PA_11
#define BT_RX      PA_12

BufferedSerial bt(BT_TX, BT_RX, 9600);

// 버튼 디바운싱을 위한 전역 변수 추가
static auto last_btn1_time = Kernel::Clock::now();
static auto last_btn2_time = Kernel::Clock::now();
static bool btn1_processed = false;
static bool btn2_processed = false;
#define DEBOUNCE_TIME_MS 300  // 300ms 디바운스 시간

// 개선된 버튼 처리 함수
int getDebounceButton() {
    auto current_time = Kernel::Clock::now();
    int btn = btnPressed();
    
    if (btn == BTN1) {
        auto time_diff = duration_cast<milliseconds>(current_time - last_btn1_time).count();
        if (time_diff > DEBOUNCE_TIME_MS && !btn1_processed) {
            last_btn1_time = current_time;
            btn1_processed = true;
            return BTN1;
        }
    }
    else if (btn == BTN2) {
        auto time_diff = duration_cast<milliseconds>(current_time - last_btn2_time).count();
        if (time_diff > DEBOUNCE_TIME_MS && !btn2_processed) {
            last_btn2_time = current_time;
            btn2_processed = true;
            return BTN2;
        }
    }
    else {
        // 버튼이 눌리지 않았을 때 플래그 리셋
        if (btn1_processed) btn1_processed = false;
        if (btn2_processed) btn2_processed = false;
    }
    
    return 0;  // 버튼이 눌리지 않음
}

void showDHT() {
   static auto last_update = Kernel::Clock::now();
   auto current_time = Kernel::Clock::now();
   
   // 1초마다만 업데이트 (OLED 부하 감소)
   if(duration_cast<milliseconds>(current_time - last_update).count() > 1000) {
      myOled.setTextSize(1);
      myOled.setTextCursor(1, 1);
      myOled.printf("Temp:%.1fC\r\nHumi:%.1f%%", temp, humidity);
      myOled.display();
      last_update = current_time;
   }
}

void showNow() {
   static auto last_update = Kernel::Clock::now();
   auto current_time = Kernel::Clock::now();
   
   // 500ms마다만 업데이트
   if(duration_cast<milliseconds>(current_time - last_update).count() > 500) {
      if (state == CLOSED || state == OPENED) {
         myOled.setTextSize(2);
         myOled.setTextCursor(65, 1);
         myOled.printf(state == CLOSED ? "Close" : "Open");
      }
      else if (state == OPENING || state == CLOSING) {
         myOled.setTextSize(3);
         myOled.setTextCursor(1, 29);
         myOled.printf(state == OPENING ? "Opening" : "Closing");
      }
      myOled.display();
      last_update = current_time;
   }
}

void setup(){
   state = CLOSED;
   ledBus = 1;
   waitTimer.attach(&waitTimerHandler, 1000ms);
   dht22Ticker.attach(&setDHTReadFlag, 1000ms);
   delay(2000ms);
   
   printf("=== Door Lock System Starting ===\n");
   printf("Testing joystick connection...\n");
   
   // 조이스틱 연결 테스트 (초기 1회)
   for(int i = 0; i < 3; i++) {
      float x_raw = xAxis.read();
      float y_raw = yAxis.read();
      printf("JS Test %d: X=%.3f, Y=%.3f\n", i+1, x_raw, y_raw);
      delay(200ms);
   }
   
   myOled.clearDisplay();
   temp = 25.0f; // 초기값 설정
   humidity = 50.0f;
   showDHT();
   showNow();
   initPwMode();
   
   printf("System ready!\n");
}

int main() {
   int melody_cnt = 0;
   int cnt = sizeof(opening_melody) / sizeof(opening_melody[0]);
   setup();
   
   while(1) {
      // DHT22 센서 읽기 (메인 룹에서 수행)
      readTempHumi();
      
      showDHT();
      
      // 개선된 버튼 처리
      int button_state = getDebounceButton();
      
      switch (state){
         case CLOSED:
            while(bt.readable()){
               char c;
               if(bt.read(&c, 1) == 1 && c == 'o'){
                  state = OPENING;
                  myOled.clearDisplay();
                  ledBus = 0;
                  wait_time = RUN_WAIT_TIME;
                  cleanupPwMode();
                  showNow();
               }
            }
            doPwModeOperation();
            
            // 개선된 버튼 처리
            if(button_state == BTN1){
               printf("BTN1 pressed - attempting password change\n");
               if(changePW()) {
                  printf("Password correct - opening door\n");
                  state = OPENING;
                  myOled.clearDisplay();
                  ledBus = 0;
                  wait_time = RUN_WAIT_TIME;
                  cleanupPwMode();
                  showNow();
               }
               else {
                  printf("Password incorrect\n");
               }
            }
            if(button_state == BTN2){
               printf("BTN2 pressed - manual open\n");
               state = OPENING;
               myOled.clearDisplay();
               ledBus = 0;
               wait_time = RUN_WAIT_TIME;
               cleanupPwMode();
               showNow();
            }
            break;
         
         case OPENING:
            motorA.forward(0.3);
            while(melody_cnt < 4){
               playMelody(opening_melody[melody_cnt]);
               delay(400ms);
               melody_cnt++;
            }
            buzzer = 0;
         
            if(wait_time <= 0){
               state = OPENED;
               myOled.clearDisplay();
               ledBus = 4;
               melody_cnt = 0;
               wait_time = FORCE_WAIT_TIME;
               motorA.stop();
               showNow();
            }
            break;
            
         case OPENED:
            while(bt.readable()){
               char c;
               if(bt.read(&c, 1) == 1 && c == 'c'){
                  state = CLOSING;
                  myOled.clearDisplay();
                  ledBus = 0;
                  wait_time = RUN_WAIT_TIME;
                  showNow();
               }
            }
            if(wait_time <= 0 || button_state == BTN2){
               if(button_state == BTN2) {
                  printf("BTN2 pressed - manual close\n");
               }
               state = CLOSING;
               myOled.clearDisplay();
               ledBus = 0;
               wait_time = RUN_WAIT_TIME;
               showNow();
            }
            break;
            
         case CLOSING:
            motorA.backward(0.3);
            while(melody_cnt < 4){
               playMelody(closing_melody[melody_cnt]);
               delay(400ms);
               melody_cnt++;
            }
            buzzer = 0;
            
            if(wait_time <= 0){
               state = CLOSED;
               myOled.clearDisplay();
               ledBus = 1;
               melody_cnt = 0;
               motorA.stop();
               showNow();
               initPwMode();
            }
            break;
      }
      
      // 메인 루프 안정성을 위한 짧은 딜레이
      delay(10ms);
   }
}