import subprocess
import sys
import os

def run_command(command):
    try:
        subprocess.run(command, check=True, shell=True)
    except subprocess.CalledProcessError as e:
        print(f"Error executing command: {command}\n{e}")

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
    
    if os.path.exists(device_path):
        print(f"Directory {device_path} already exists. Exiting.")
        sys.exit(0)

    command_01 = f"mkdir -p {device_path}/en_{device_id}"
    run_command(command_01)

    # 复制ca
    command_02 = f"cp {base_path}/ca.crt {device_path}"
    run_command(command_02)
    
    # 生成设备私钥
    command_03 = f"openssl genpkey -algorithm RSA -out {key_path} -pkeyopt rsa_keygen_bits:2048"
    run_command(command_03)

    # 生成设备证书签发请求（CSR）
    command_04 = f"openssl req -new -key {key_path} -out {csr_path} -config {cnf_path} -subj \"/C=CN/ST=Beijing/L=Beijing/O=Wakamda/CN={device_id}\""
    run_command(command_04)

    # 生成设备证书
    command_05 = f"openssl ca -in {csr_path} -out {crt_path} -config {cnf_path}"
    run_command(command_05)

    # 更新 CRL 文件
    command_06 = f"openssl ca -gencrl -out {crl_path} -config {cnf_path}"
    run_command(command_06)

    # 生成加密p12
    command_07 = f"openssl pkcs12 -export -out {p12_path} -inkey {key_path} -in {crt_path} -passout pass:{device_id}"
    run_command(command_07)

    # 生成二次加密p12文件
    command_08 = f"python3 {encrypt_py_path} {p12_path} {ca_path} {en_p12_path} {en_ca_path} {device_id}"
    run_command(command_08)

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 a.py <device_id>")
        sys.exit(1)
    
    device_id = sys.argv[1]
    
    # 执行命令
    generate_device_certificates(device_id)
