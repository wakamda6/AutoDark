# 2024.12.20
版本：1.5.6
1. 修改mqtt前台服务和主页面的关系：删除绑定关系，删除callback方法，修改为普通前台服务，使用startService(Intent(this, MqttService::class.java))启动，并由单向广播器负责打卡结果发送。
2. 删除NotificationMonitorService中的本地广播器创建，修改为使用单向广播器负责打卡结果发送。
3. 优化main activity：使用源项目的1.6.0版本中的main activity
4. 添加根据唯一设备ID来设置mqtt订阅主题的功能,并在程序每一次启动时向邮箱发送主题内容