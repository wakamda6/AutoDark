import sys
import hashlib
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad
import os

# 获取字符串的SHA256哈希值
def get_string_hash(string):
    sha256 = hashlib.sha256()
    sha256.update(string.encode('utf-8'))  # 将字符串编码为字节
    return sha256.digest()

# 对文件进行AES加密
def aes_encrypt(input_file_path, output_file_path, key):
    # AES密钥需要是16字节、24字节或者32字节（对应AES-128, AES-192, AES-256）
    cipher = AES.new(key, AES.MODE_CBC)
    with open(input_file_path, 'rb') as f:
        file_data = f.read()

    # 填充文件内容至块大小（AES块大小为16字节）
    padded_data = pad(file_data, AES.block_size)
    
    # 加密
    encrypted_data = cipher.encrypt(padded_data)

    # 保存加密后的数据，前面保存IV（初始化向量）
    with open(output_file_path, 'wb') as f:
        f.write(cipher.iv)  # 存储IV（前16字节）
        f.write(encrypted_data)

# 生成密钥：基于传入的字符串进行哈希值 + 1
def generate_key_from_string(input_string):
    # 计算字符串的哈希值（使用SHA256）
    string_hash = get_string_hash(input_string)

    # 将哈希值每个字节加1
    transformed_hash = bytes([(b + 1) % 256 for b in string_hash])

    # 使用前16字节作为AES密钥
    return transformed_hash[:16]

def main():
    if len(sys.argv) != 6:
        print("Usage: python3 encrypt_files.py <p12_file> <ca_file> <output_p12_file> <output_ca_file> <secret_string>")
        sys.exit(1)

    # 从命令行获取输入和输出文件路径
    p12_file = sys.argv[1]
    ca_file = sys.argv[2]
    output_p12_file = sys.argv[3]
    output_ca_file = sys.argv[4]
    secret_string = sys.argv[5]  # 获取密钥生成基础的字符串参数

    # 生成用于加密的密钥（基于字符串生成）
    key = generate_key_from_string(secret_string)

    # 加密 p12 文件
    print(f"Encrypting {p12_file}...")
    aes_encrypt(p12_file, output_p12_file, key)

    # 加密 ca.cert 文件
    print(f"Encrypting {ca_file}...")
    aes_encrypt(ca_file, output_ca_file, key)

    print("Files encrypted successfully.")

if __name__ == "__main__":
    main()
