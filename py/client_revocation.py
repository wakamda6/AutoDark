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

    index_path = f"{base_path}/index"

    crl_path =  f"{base_path}/crl/crl.pem"

    pem_path = f"{base_path}/newcerts/{pem_id}.pem"

    # 根据pem获取设备 ID
    device_id = find_cn_by_serial(pem_id)
    device_path = f"{base_path}/certs/{device_id}"

    if not os.path.exists(device_path):
        if not os.path.exists(pem_path):
            print(f"Directory {device_path} and pem file {pem_path} not exists")
            return

    #吊销证书
    command_01 = f"openssl ca -revoke {pem_path} -config {cnf_path}"
    run_command(command_01)

    #更新crl文件
    command_02 = f"openssl ca -gencrl -out {crl_path} -config {cnf_path}"
    run_command(command_02)

    #查询crl.pem
    command_03 = f"openssl crl -in {crl_path} -text -noout"
    print(f"查询crl.pem内容如下：\n")
    run_command(command_03)

    #删除newcerts中对应的pem文件
    command_04 = f"rm {pem_path}"
    run_command(command_04, check_file=pem_path)

    # 删除整个文件夹
    command_05 = f"rm -rf {device_path}"
    run_command(command_05, check_file=device_path)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 a.py pem文件名，即下表第3列")
        command = f"cat /etc/ca_Autodark/index"
        run_command(command)
        sys.exit(1)
    
    pem_id = sys.argv[1]

    generate_device_certificates(pem_id)

    command2 = f"cat /etc/ca_Autodark/index"
    run_command(command2)

