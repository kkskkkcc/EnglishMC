package io.github.kkskkkcc.englishmc.client.speech;

import com.google.gson.Gson;
import io.github.kkskkkcc.englishmc.EnglishMC;
import io.github.kkskkkcc.englishmc.LessonDeck.Bilingual;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class LocalVoice {
    private static final Gson JSON = new Gson();
    private static final String SYNTHESIZE = """
        $ErrorActionPreference = 'Stop'
        [Console]::InputEncoding = [Text.UTF8Encoding]::new($false)
        [Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
        try {
            $lines = ConvertFrom-Json ([Console]::ReadLine())
            Add-Type -AssemblyName System.Speech
            $synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
            try {
                $synth.SetOutputToDefaultAudioDevice()
                $voices = @{}
                foreach ($voice in $synth.GetInstalledVoices()) {
                    if ($voice.Enabled -and !$voices.ContainsKey($voice.VoiceInfo.Culture.Name)) {
                        $voices[$voice.VoiceInfo.Culture.Name] = $voice.VoiceInfo.Name
                    }
                }
                $missing = @{}
                foreach ($line in $lines) {
                    foreach ($part in @(@{Language='en-US'; Text=$line.english},
                                         @{Language='zh-CN'; Text=$line.chinese})) {
                        if ([string]::IsNullOrWhiteSpace($part.Text)) { continue }
                        if (!$voices.ContainsKey($part.Language)) {
                            $missing[$part.Language] = $true
                            continue
                        }
                        $synth.SelectVoice($voices[$part.Language])
                        $synth.Rate = -1
                        $synth.Speak([string]$part.Text)
                    }
                }
                foreach ($language in $missing.Keys) { [Console]::WriteLine($language) }
            } finally { $synth.Dispose() }
        } catch {
            [Console]::Error.WriteLine($_.Exception.Message)
            exit 1
        }
        """;
    private static final String COMMAND = Base64.getEncoder().encodeToString(SYNTHESIZE.getBytes(StandardCharsets.UTF_16LE));
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "EnglishMC speech playback");
        thread.setDaemon(true);
        return thread;
    });
    private int revision;
    private Process running;

    public synchronized void speak(List<Bilingual> lines, Consumer<String> status) {
        stop();
        int request = revision;
        worker.execute(() -> play(request, lines, status));
    }

    public synchronized void stop() {
        revision++;
        if (running != null) {
            running.destroyForcibly();
            running = null;
        }
    }

    private void play(int request, List<Bilingual> lines, Consumer<String> status) {
        Process process;
        try {
            synchronized (this) {
                if (request != revision) return;
                String system = System.getenv("SystemRoot");
                if (system == null) throw new IOException("Windows PowerShell is unavailable");
                String executable = Path.of(system, "System32", "WindowsPowerShell", "v1.0", "powershell.exe").toString();
                running = process = new ProcessBuilder(executable, "-NoLogo", "-NoProfile",
                    "-NonInteractive", "-EncodedCommand", COMMAND).redirectErrorStream(true).start();
            }
            try (var input = process.getOutputStream()) {
                input.write((JSON.toJson(lines) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            String response = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            int exit = process.waitFor();
            synchronized (this) {
                if (request != revision) return;
                running = null;
                if (exit != 0) {
                    EnglishMC.LOG.warn("EnglishMC speech synthesis failed: {}", response);
                    status.accept("englishmc.status.voice_failed");
                } else if (!response.isEmpty()) {
                    status.accept("englishmc.status.voice_missing");
                }
            }
        } catch (IOException | InterruptedException e) {
            synchronized (this) {
                if (request != revision) return;
                stop();
                EnglishMC.LOG.warn("EnglishMC speech synthesis could not start", e);
                status.accept("englishmc.status.voice_failed");
            }
        }
    }
}
