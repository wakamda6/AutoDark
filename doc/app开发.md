# 初始版本

1.5.3

## topic设置

qos全部设置为1

**#** **mqtt代理地址**

tcp://39.106.230.248:1883

**#** **客户端ID规则**

由客户端自己生成唯一设备ID，示例ID：ffffffff-be15-aab7-0000-0000026581f0

**#** **连接安全性**

用户名和密码都是客户端自己的唯一ID

**#** **主题分类及消息内容**

\1. /topic/xxID/checkAppAlive:

  "isAlive?"

\2. /topic/xxID/checkAppAliveResult:

  alive/无返回

\3. /topic/xxID/dark:

  dark

\4. /topic/xxID/darkResult:

  打卡结果通知

\5. /topic/xxID/LastWill



\> result主题不需要客户端订阅



**#** **例外**

esp32设备为例外设备，其ID,用户名，密码都为00000000-0000-0000-0000-000000000000



## mqtt从mainactivity中修改为单独的前台服务

并在其中添加网络变化接收器和广播接收器

网络变化接收器：用于在网络发生变化时重连

广播接收器：用于传输其他类的打卡结果到mqtt服务；还可以用于其他地方。



### mqtt和mainactivity关系转变为绑定关系

当mainactivity创建时，mqtt服务启动，当销毁时，mainactivity和mqtt解绑，**并且mqtt要调起mainactivity。**



## 功能

mqtt前台服务最大的功能就是正常接收mqtt消息，保证系统不会销毁这个服务

另外还有打卡结果数据要通过mqtt进行发送，因此，mqtt和mainactivity的正常通信也要保证。



前台服务必须要绑定一个app，所以只能设置当app调起钉钉时，mqtt不断开；但是如果app被销毁，那么mqtt也就连不上了。

