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

def generate_device_certificates(device_id):
    # 设置设备路径和文件名
    base_path   = f"/etc/ca_Autodark"
    device_path = f"{base_path}/certs/{device_id}"

    key_path = f"{device_path}/{device_id}.key"
    csr_path = f"{device_path}/{device_id}.csr"
    crt_path = f"{device_path}/{device_id}.crt"
    p12_path = f"{device_path}/{device_id}.p12"
    ca_path  = f"{device_path}/ca.crt"
    
    encrypt_file_path = f"{device_path}/en_{device_id}"
    en_p12_path= f"{encrypt_file_path}/{device_id}.en"
    en_ca_path  = f"{encrypt_file_path}/ca.en"

    cnf_path = f"{base_path}/ca_Autodark.cnf"

    crl_path =  f"{base_path}/crl/crl.pem"

    encrypt_py_path = f"{base_path}/certs/generate_key_and_encrypt_p12_and_crt.py"

    # 创建存放相关文件的文件夹
    command_01 = f"mkdir -p {device_path}/en_{device_id}"
    run_command(command_01, check_file=f"{device_path}/en_{device_id}")

    # 复制ca
    command_02 = f"cp {base_path}/ca.crt {device_path}"
    run_command(command_02, check_file=f"{device_path}/ca.crt")
    
    # 生成设备私钥
    command_03 = f"openssl genpkey -algorithm RSA -out {key_path} -pkeyopt rsa_keygen_bits:2048"
    run_command(command_03, check_file=key_path)

    # 生成设备证书签发请求（CSR）
    command_04 = f"openssl req -new -key {key_path} -out {csr_path} -config {cnf_path} -subj \"/C=CN/ST=Beijing/L=Beijing/O=Wakamda/CN={device_id}\""
    run_command(command_04, check_file=csr_path)

    # 生成设备证书
    command_05 = f"openssl ca -in {csr_path} -out {crt_path} -config {cnf_path}"
    run_command(command_05, check_file=crt_path)

    # 更新 CRL 文件
    command_06 = f"openssl ca -gencrl -out {crl_path} -config {cnf_path}"
    run_command(command_06)

    # 生成加密p12
    command_07 = f"openssl pkcs12 -export -out {p12_path} -inkey {key_path} -in {crt_path} -passout pass:{device_id}"
    run_command(command_07, check_file=p12_path)

    # 生成二次加密p12文件
    command_08 = f"python3 {encrypt_py_path} {p12_path} {ca_path} {en_p12_path} {en_ca_path} {device_id}"
    run_command(command_08, check_file=en_p12_path)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: <device_id>")
        sys.exit(1)
    
    device_id = sys.argv[1]
    
    # 执行命令
    generate_device_certificates(device_id)

    #查询
    command = f"cat /etc/ca_Autodark/index"
    run_command(command)
