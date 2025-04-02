import subprocess
import sys
import os

def run_command(command):
    try:
        subprocess.run(command, check=True, shell=True)
    except subprocess.CalledProcessError as e:
        print(f"Error executing command: {command}\n{e}")

def find_cn_by_serial(serial_number):
    """在 index 文件中查找给定序列号对应的 CN"""
    index_file = "/etc/ca_Autodark/index"
    
    with open(index_file, "r") as f:
        lines = f.readlines()
    
    for line in lines:
        fields = line.strip().split("\t")
        if len(fields) >= 6 and fields[3] == serial_number:
            dn = fields[-1]  # 获取 DN 字段
            if dn.startswith("/C="):
                cn_field = [x for x in dn.split("/") if x.startswith("CN=")]
                if cn_field:
                    return cn_field[0][3:]  # 提取 CN 值
    return None

def generate_device_certificates(pem_id):
    # 设置设备路径和文件名
    base_path   = f"/etc/ca_Autodark"
    
    cnf_path = f"{base_path}/ca_Autodark.cnf"

    crl_path =  f"{base_path}/crl/crl.pem"

    pem_path = f"{base_path}/newcerts/{pem_id}.pem"
    
    if os.path.exists(pem_path):
        #吊销证书
        command_01 = f"openssl ca -revoke {pem_path} -config {cnf_path}"
        run_command(command_01)
        command_02 = f"rm {pem_path}"
        run_command(command_02)

        #更新crl文件
        command_03 = f"openssl ca -gencrl -out {crl_path} -config {cnf_path}"
        run_command(command_03)
    else:
        print(f"Directory {pem_path} not exists")
        return

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 a.py pem文件名")
        command = f"cat /etc/ca_Autodark/index"
        run_command(command)
        sys.exit(1)
    
    pem_id = sys.argv[1]
    
    # 执行命令
    generate_device_certificates(pem_id)

    # 根据pem获取设备 ID
    device_id = find_cn_by_serial(pem_id)

    # 删除设备文件夹
    base_path   = f"/etc/ca_Autodark"
    device_path = f"{base_path}/certs/{device_id}"
    if not os.path.exists(device_path):
        print(f"Directory {device_path} not exists. Exiting.")
        sys.exit(0)

    command_03 = f"rm -rf {device_path}"
    run_command(command_03)
    print(f"Directory {device_path} deleted.")

