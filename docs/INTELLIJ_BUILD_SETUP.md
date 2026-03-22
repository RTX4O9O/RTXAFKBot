# IntelliJ IDEA Build Configuration for Obfuscated Plugin

## ✅ Configuration Complete

I've set up IntelliJ IDEA to build your plugin with obfuscation using the built-in Maven integration.

## 🚀 **How to Build with Obfuscation in IntelliJ:**

### Method 1: Using Run Configurations (Recommended)
1. **Click the dropdown** next to the green ▶️ button in IntelliJ
2. **Select "Build Plugin (Obfuscated)"**
3. **Click the green ▶️ button**
4. ✅ **Result:** `target/fpp-1.4.22-obfuscated.jar` will be created

### Method 2: Using Maven Tool Window
1. **Open Maven tool window** (View → Tool Windows → Maven)
2. **Expand your project** → Lifecycle
3. **Double-click "package"** (this runs `mvn clean package` with obfuscation)
4. ✅ **Result:** Obfuscated JAR created automatically

### Method 3: Using Build Menu
1. **Go to Build** → Build Project (Ctrl+F9)
2. **IntelliJ will use Maven** to build with obfuscation
3. ✅ **Result:** Both debug and obfuscated JARs created

## 📁 **Build Output Files:**

After any build, check `target/` folder:
- 🔒 **`fpp-1.4.22-obfuscated.jar`** ← **Deploy this one** (15.31 MB)
- 🐛 **`fpp-1.4.22.jar`** ← Debug only (16.32 MB)  
- 📦 **`original-fpp-1.4.22.jar`** ← Pre-shading backup (0.32 MB)

## ⚙️ **What Was Configured:**

1. **✅ Maven Integration:** Set to delegate builds to Maven
2. **✅ Run Configurations:** Created "Build Plugin (Obfuscated)" 
3. **✅ Default Goals:** Maven will run `clean package` (includes ProGuard)
4. **✅ Auto-Build:** IntelliJ will use Maven for all builds

## 🔧 **Advanced Options:**

### Quick Development Build (No Obfuscation)
- Use the "Quick Build (No Obfuscation)" run configuration
- This runs `mvn clean compile` for faster development

### Automatic Build on Changes  
- **File** → **Settings** → **Build, Execution, Deployment** → **Compiler**
- **✅ Enable "Build project automatically"**
- Now every save triggers a build with obfuscation

## ✅ **Verification:**

1. **Build the project** using any method above
2. **Check target folder** - should contain `fpp-1.4.22-obfuscated.jar`
3. **Verify obfuscation** - classes should be `o/tC.class`, `o/tD.class`, etc.

**Your IntelliJ IDEA is now configured to build with obfuscation by default!** 🎉
