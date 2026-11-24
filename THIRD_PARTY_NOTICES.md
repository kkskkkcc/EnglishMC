# 第三方组件

| 组件 | 版本、来源和许可 |
| --- | --- |
| Kokoro 82M bilingual model | [Kokoro-82M-v1.1-zh](https://huggingface.co/hexgrad/Kokoro-82M-v1.1-zh) · Apache 2.0 · [Sherpa 的 int8 模型包](https://github.com/k2-fsa/sherpa-onnx/releases/tag/tts-models) |
| sherpa-onnx | [v1.12.39 Java API 与 Windows x64 JNI](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v1.12.39) · Apache 2.0 |
| ONNX Runtime | [源代码和 MIT 许可](https://github.com/microsoft/onnxruntime) · 随 sherpa-onnx JNI 分发 |
| eSpeak NG | [源代码和许可](https://github.com/espeak-ng/espeak-ng) · GPLv3 或更新 · 上游 Sherpa 运行时与模型包含其组件及语言数据 |
| Vosk API / libvosk | [0.3.45 源代码](https://github.com/alphacep/vosk-api/tree/v0.3.45) · [Maven Central 二进制](https://repo.maven.apache.org/maven2/com/alphacephei/vosk/0.3.45/) · Apache 2.0 |
| Vosk US English acoustic model | [vosk-model-small-en-us-0.15](https://alphacephei.com/vosk/models) · Apache 2.0 |
| Kaldi | Vosk 底层识别库 · [源代码和 Apache 2.0 许可](https://github.com/kaldi-asr/kaldi) |
| GCC runtime | 上游 Windows Vosk 包中的 `libgcc_s_seh-1.dll` 与 `libstdc++-6.dll` · [GCC 源代码](https://gcc.gnu.org/git.html) · GPLv3 加 [GCC 运行库例外 3.1](https://www.gnu.org/licenses/gcc-exception-3.1.html) |
| mingw-w64 winpthreads | 上游 Windows Vosk 包中的 `libwinpthread-1.dll` · [源代码和许可](https://github.com/mingw-w64/mingw-w64/tree/master/mingw-w64-libraries/winpthreads) |
| Gradle Wrapper | [Gradle 8.5](https://github.com/gradle/gradle/tree/v8.5.0) · Apache 2.0 |

Minecraft、Forge 和 Java 运行时由玩家的运行环境提供。
