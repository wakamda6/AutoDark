import subprocess
import sys
import os

def run_command(command, check_file=None):
    # 如果需要检查某个文件/目录是否已经存在，若存在则跳过执行
    if check_file and os.path.exists(check_file):
        print(f"✅ Step already completed: {check_file} exists, skipping command.")
        return

    print(f"[RUNNING] 执行命令：{command}")
    try:
        subprocess.run(command, check=True, shell=True)
        print(f"[SUCCESS] 命令执行成功")
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] 命令执行失败\n原因：{e}")
        sys.exit(1)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage:<device_id>")
        sys.exit(1)

    device_id = sys.argv[1]

    #Ca证书权限
    command1 = f"python3 /etc/ca_Autodark/py/client_generate.py {device_id}"
    run_command(command1)

    #mosquitto权限
    command2 = f"mosquitto_passwd -b /etc/mosquitto/pwfile.txt {device_id} {device_id}"
    run_command(command2)

    #热更新mosquitto
    command3 = f"kill -HUP $(pidof mosquitto)"
    run_command(command3)

    #查询结果
    command4 = f"cat /etc/mosquitto/pwfile.txt | grep {device_id} "
    run_command(command4)