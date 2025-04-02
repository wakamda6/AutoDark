# mosquitto
```
//首次添加
mosquitto_passwd -c /etc/mosquitto/pwfile.txt username
//第二次添加不需要使用 -c 选项（它会创建一个新文件并覆盖现有文件）
mosquitto_passwd /etc/mosquitto/pwfile.txt another_username
//删除
mosquitto_passwd -D /etc/mosquitto/pwfile.txt another_username
```

# 加密设置

mqtt代理地址： ssl://***REMOVED***:8883

仅支持双向认证，TLS版本为1.3

## topic设置

qos全部设置为1

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

  darkPhone_offline_at_+年月日时分秒

\> result主题不需要客户端订阅,仅订阅checkAppAlive和dark并设置遗嘱