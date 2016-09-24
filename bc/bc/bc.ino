// Arduino Wire library is required if I2Cdev I2CDEV_ARDUINO_WIRE implementation
// is used in I2Cdev.h
#include "Wire.h"

// I2Cdev and MPU6050 must be installed as libraries, or else the .cpp/.h files
// for both classes must be in the include path of your project
#include "I2Cdev.h"
#include "MPU6050.h"
// class default I2C address is 0x68
// specific I2C addresses may be passed as a parameter here
// AD0 low = 0x68 (default for InvenSense evaluation board)
// AD0 high = 0x69
MPU6050 accelgyro;
#define OUT1A 6
#define OUT1B 8
#define OUT2A 5
#define OUT2B 7
#define my_Serial Serial1 //定义串口通讯为串口1
String msg = "mcookie"; 
int status=0,face=0;
int16_t ax, ay, az;
int16_t gx, gy, gz;
long theta;
int tg=10;
int dtheta=0;
int dl=0,dr=0,r=1,l=1;
int token=0;
int counter=0;
int num=0;
float p=0.070;
float d=0.0040;
void motor(int v, int l, int r, int dl, int dr, int s)
{
  if (status == 0){
    r = 1, l = 1;
    if (v > 0)
   {
     if (v > 255){
       v = 255;
     }
     analogWrite(OUT1A, v / l+dl);  //将速度级别写入电机
     digitalWrite(OUT1B, LOW);
     analogWrite(OUT2A, v / r+dr);
     digitalWrite(OUT2B, LOW);
   }
   else if (v < 0)
   {
     if (v < -255){
       v = -255;
     }
     v = -v;
     digitalWrite(OUT1A, LOW);
     analogWrite(OUT1B, v / l+dr);  //将速度级别写入电机
     digitalWrite(OUT2A, LOW);
     analogWrite(OUT2B, v / r+dl);
   }
   else
   {

     digitalWrite(OUT1A, LOW);
     digitalWrite(OUT1B, LOW);
     digitalWrite(OUT2A, LOW);
     digitalWrite(OUT2B, LOW);
   }
  }
  else{
    if (v > 0)
    {
      if (v > 255){
        v = 255;
      }
      analogWrite(OUT1A, v / l);  //将速度级别写入电机
      digitalWrite(OUT1B, LOW);
      analogWrite(OUT2A, v / r);
      digitalWrite(OUT2B, LOW);
    }
    else if (v < 0)
    {
      if (v < -255){
        v = -255;
      }
      v = -v;
      digitalWrite(OUT1A, LOW);
      analogWrite(OUT1B, v / l);  //将速度级别写入电机
      digitalWrite(OUT2A, LOW);
      analogWrite(OUT2B, v / r);
    }
    else
    {

      digitalWrite(OUT1A, LOW);
      digitalWrite(OUT1B, LOW);
      digitalWrite(OUT2A, LOW);
      digitalWrite(OUT2B, LOW);
    }
  }
    
}

int locktheta(long theta,int ax){
		
		theta = theta  + gy*0.015;
		theta = theta + (ax - theta) / tg;
	return theta;
}
int anglecontrol(long theta,int k,int initial,float p,float d){
  int v;
  v=(theta-initial)*p+k*d;
  return v;
}
void setup() {
    // 初始化蓝牙通信波特率
  my_Serial.begin(9600);
  // 初始化串口监视器通信波特率
  Serial.begin(9600);
    // join I2C bus (I2Cdev library doesn't do this automatically)
    Wire.begin();
    // initialize serial communication
    // (38400 chosen because it works as well at 8MHz as it does at 16MHz, but
    // it's really up to you depending on your project)
    Serial.begin(38400);
    // initialize device
    Serial.println("Initializing I2C devices...");
    accelgyro.initialize();
    // verify connection
    Serial.println("Testing device connections...");
    Serial.println(accelgyro.testConnection() ? "MPU6050 connection successful" : "MPU6050 connection failed");
    // configure Arduino LED for
    pinMode(OUT1A, OUTPUT);
    pinMode(OUT1B, OUTPUT);
    pinMode(OUT2A, OUTPUT);
    pinMode(OUT2B, OUTPUT);
    accelgyro.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
    theta=ax;
}
void loop() {
  
  if(theta/180>17){token=1;}
  if(token==1&&counter<30){
    num=num+theta/180;
    counter++;
  }
  if(counter==30){
    if(num/30>17){ my_Serial.println(msg); Serial.println(token);}
    counter=0;
    token=0;
    num=0;
    Serial.println(token);
   }
    if (my_Serial.available() > 0)  //如果串口有数据输入
  {
    msg = my_Serial.readStringUntil('\n'); //获取换行符前所有的内容
  }
  
    accelgyro.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
    if(msg=="1"){p=0.070;d=0.0040;dtheta=0;face=0;}
    if(msg=="2"){p=0.070;d=0.0040;dtheta=0;status=0;face=1;}
    if(msg=="w"){dtheta=-16;status=1;}
    if(msg=="s"){dtheta=16;status=-1;}
    if(msg=="a"){dr=30;dl=0;r=20;l=1;}
    if(msg=="d"){dr=0;dl=30;l=20;r=1;}
    if(msg=="wad"){dtheta=25;}
    if(msg=="sad"){dtheta=-25;}
    if(msg=="aad"){dr=70;dl=0;r=40;l=1;}
    if(msg=="dad"){dr=0;dl=70;l=40;r=1;}
    if(msg=="stop"){dtheta=0;status=0;}
    if(msg=="stopturn"){dr=0;dl=0;l=1;r=1;}
    if(msg=="round"){}
    if(msg=="dance"){}

	  theta = locktheta(theta, ax);
    int v=-anglecontrol( theta, gy,-6*180+dtheta*180,p,d);
    motor(v,l,r,dl,dr,status);
    Serial.print("a/g:\t");
    Serial.print(ax/180); Serial.print("\t");
    Serial.print(v);Serial.print("\t");
    Serial.print(theta/180); Serial.print("\t");
    Serial.println(gy/180);
    delay(10);
    // blink LED to indicate activity
}
