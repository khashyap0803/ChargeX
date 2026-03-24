import os
import shutil
import re

def rename_content():
    project_dir = r"d:\Documents\shreeya\ChargeX"
    
    # 1. Move directories
    base_src = os.path.join(project_dir, "app", "src")
    if os.path.exists(base_src):
        for flavor in os.listdir(base_src):
            flavor_dir = os.path.join(base_src, flavor, "java")
            old_pkg = os.path.join(flavor_dir, "net", "vonforst", "evmap")
            new_pkg = os.path.join(flavor_dir, "com", "chargex", "india")
            
            if os.path.exists(old_pkg):
                print(f"Moving {old_pkg} to {new_pkg}")
                os.makedirs(os.path.dirname(new_pkg), exist_ok=True)
                shutil.move(old_pkg, new_pkg)
                
                # Try to clean up empty net/vonforst dirs
                try:
                    os.rmdir(os.path.join(flavor_dir, "net", "vonforst"))
                    os.rmdir(os.path.join(flavor_dir, "net"))
                except:
                    pass

    # 2. Find and replace in files
    extensions = {'.kt', '.java', '.xml', '.pro', '.kts', '.md', '.json'}
    
    for root, dirs, files in os.walk(project_dir):
        # Skip git, build, etc.
        if '.git' in root or '\\build\\' in root or '\\.gradle\\' in root:
            continue
            
        for file in files:
            if not any(file.endswith(ext) for ext in extensions):
                continue
                
            file_path = os.path.join(root, file)
            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    
                new_content = content
                
                # Replace package string
                new_content = new_content.replace('net.vonforst.evmap', 'com.chargex.india')
                new_content = new_content.replace('net/vonforst/evmap', 'com/chargex/india')
                
                # Case sensitive replacements
                new_content = new_content.replace('EVMap', 'ChargeX')
                new_content = new_content.replace('evmap', 'chargex')
                new_content = new_content.replace('EvMap', 'ChargeX')
                
                if content != new_content:
                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(new_content)
                    print(f"Updated: {file_path}")
            except Exception as e:
                print(f"Failed to process {file_path}: {e}")

    # 3. Rename files referencing evmap
    for root, dirs, files in os.walk(base_src):
        for file in files:
            if 'evmap' in file.lower():
                old_path = os.path.join(root, file)
                new_file = file.replace('evmap', 'chargex').replace('EVMap', 'ChargeX')
                new_path = os.path.join(root, new_file)
                print(f"Renaming file {old_path} to {new_path}")
                os.rename(old_path, new_path)

if __name__ == "__main__":
    rename_content()
