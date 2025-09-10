#include <jni.h>
#include <string>
#include "piper.h"

extern "C" JNIEXPORT jlong JNICALL
Java_com_piper_tts_PiperTtsService_nativeCreateSynthesizer(
        JNIEnv *env,
        jobject /* this */,
        jstring modelPath,
        jstring configPath,
        jstring espeakDataPath) {

    const char *modelPathCStr = env->GetStringUTFChars(modelPath, nullptr);
    const char *configPathCStr = env->GetStringUTFChars(configPath, nullptr);
    const char *espeakDataPathCStr = env->GetStringUTFChars(espeakDataPath, nullptr);

    piper_synthesizer *synth = piper_create(modelPathCStr, configPathCStr, espeakDataPathCStr);

    env->ReleaseStringUTFChars(modelPath, modelPathCStr);
    env->ReleaseStringUTFChars(configPath, configPathCStr);
    env->ReleaseStringUTFChars(espeakDataPath, espeakDataPathCStr);

    return reinterpret_cast<jlong>(synth);
}

extern "C" JNIEXPORT void JNICALL
Java_com_piper_tts_PiperTtsService_nativeDeleteSynthesizer(
        JNIEnv *env,
        jobject /* this */,
        jlong synthesizerPtr) {
    piper_synthesizer *synth = reinterpret_cast<piper_synthesizer *>(synthesizerPtr);
    if (synth) {
        piper_free(synth);
    }
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_piper_tts_PiperTtsService_nativeSynthesize(
        JNIEnv *env,
        jobject /* this */,
        jlong synthesizerPtr,
        jstring text) {

    piper_synthesizer *synth = reinterpret_cast<piper_synthesizer *>(synthesizerPtr);
    if (!synth) {
        return nullptr;
    }

    const char *textCStr = env->GetStringUTFChars(text, nullptr);

    piper_synthesize_options options = piper_default_synthesize_options(synth);
    int result = piper_synthesize_start(synth, textCStr, &options);

    env->ReleaseStringUTFChars(text, textCStr);

    if (result != PIPER_OK) {
        return nullptr;
    }

    std::vector<float> audio_buffer;
    piper_audio_chunk chunk;
    while (piper_synthesize_next(synth, &chunk) != PIPER_DONE) {
        audio_buffer.insert(audio_buffer.end(), chunk.samples, chunk.samples + chunk.num_samples);
    }

    jfloatArray audioArray = env->NewFloatArray(audio_buffer.size());
    env->SetFloatArrayRegion(audioArray, 0, audio_buffer.size(), audio_buffer.data());

    return audioArray;
}
