import subprocess
import sys
import os

def run_command(command, check_file=None):
    # 如果需要检查某个文件/目录是否已经删除，若删除则跳过执行
    if check_file is not None and not os.path.exists(check_file):
        print(f"[SKIP] {check_file} 不存在，跳过：{command}")
        return

    print(f"[RUNNING] 执行命令：{command}")
    try:
        subprocess.run(command, check=True, shell=True)
        print(f"[SUCCESS] 命令执行成功")
    except subprocess.CalledProcessError as e:
        print(f"[ERROR] 命令执行失败\n原因：{e}")
        sys.exit(1)

def find_cert_id_by_cn(file_path, device_id):
    """
    从证书文件中查找以 V 开头，且 CN 匹配 target_cn 的证书 ID（第三列）

    参数:
        file_path (str): 证书数据文件路径
        target_cn (str): 要匹配的 CN 值，例如 "202a36f2838cecd9"

    返回:
        str or None: 匹配到的 ID（第三列），如果没有找到返回 None
    """
    with open(file_path, "r") as f:
        for line in f:
            if line.startswith("V") and f"CN={device_id}" in line:
                parts = line.split()
                if len(parts) > 2:
                    return parts[2]
    return None



if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage:<device_id>")
        sys.exit(1)

    device_id = sys.argv[1]

    #mosquitto权限删除
    command1 = f"mosquitto_passwd -D /etc/mosquitto/pwfile.txt {device_id}"
    run_command(command1)

    #热更新mosquitto
    command2 = f"kill -HUP $(pidof mosquitto)"
    run_command(command2)

    #Ca证书权限删除
    pem = find_cert_id_by_cn("/etc/ca_Autodark/index", device_id)
    command3 = f"python3 /etc/ca_Autodark/py/client_revocation.py {pem}"
    run_command(command3)