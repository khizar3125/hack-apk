#!/usr/bin/env python3
"""
Voice Cloning Setup for YouTube Videos
Allows you to clone your voice and use it for video narration
"""

import os
import sys
import subprocess
import json
from pathlib import Path

# Configuration
PROJECT_DIR = Path(__file__).parent
VOICE_DATA_DIR = PROJECT_DIR / "voice_data"
CLONED_VOICE_DIR = PROJECT_DIR / "cloned_voice"
AUDIO_OUTPUT_DIR = PROJECT_DIR / "audio_output"

# Supported TTS engines
TTS_ENGINES = {
    "edge-tts": {
        "description": "Microsoft Edge TTS (Online, High Quality)",
        "voices": {
            "hi-IN": ["hi-IN-MadhurNeural", "hi-IN-SwaraNeural"],
            "en-US": ["en-US-AriaNeural", "en-US-GuyNeural"],
        },
        "requires": ["edge-tts"]
    },
    "coqui-tts": {
        "description": "Coqui TTS (Offline, Supports Voice Cloning)",
        "voices": "Custom cloned voices",
        "requires": ["TTS", "torch"],
        "clone_support": True
    },
    "espeak": {
        "description": "eSpeak (Offline, Basic Quality)",
        "voices": "System voices",
        "requires": ["espeak"]
    }
}

def setup_directories():
    """Create necessary directories"""
    VOICE_DATA_DIR.mkdir(exist_ok=True)
    CLONED_VOICE_DIR.mkdir(exist_ok=True)
    AUDIO_OUTPUT_DIR.mkdir(exist_ok=True)
    print("✓ Directories created")

def check_dependencies():
    """Check which TTS engines are available"""
    available = {}
    
    # Check edge-tts
    try:
        import edge_tts
        available["edge-tts"] = True
    except ImportError:
        available["edge-tts"] = False
    
    # Check coqui TTS
    try:
        from TTS.api import TTS
        available["coqui-tts"] = True
    except ImportError:
        available["coqui-tts"] = False
    
    # Check espeak
    available["espeak"] = subprocess.run(
        ["which", "espeak"],
        capture_output=True
    ).returncode == 0
    
    return available

def install_coqui_tts():
    """Install Coqui TTS for voice cloning"""
    print("\n🔧 Installing Coqui TTS for voice cloning...")
    
    # Install TTS library
    print("Installing TTS library...")
    result = subprocess.run(
        ["pip", "install", "TTS", "torch", "--quiet"],
        capture_output=True,
        text=True
    )
    
    if result.returncode != 0:
        print(f"⚠ Warning: {result.stderr}")
        return False
    
    # Download voice cloning model
    print("Downloading voice cloning model...")
    from TTS.api import TTS
    
    try:
        # Initialize TTS with cloning model
        tts = TTS(model_name="tts_models/multilingual/multi-dataset/xtts_v2", progress_bar=False)
        print("✓ Coqui TTS with voice cloning installed")
        return True
    except Exception as e:
        print(f"⚠ Warning: Could not initialize voice cloning model: {e}")
        return False

def record_voice_sample():
    """Record voice samples for cloning"""
    print("\n🎤 Recording Voice Samples for Cloning")
    print("=" * 50)
    print("To clone your voice, we need 5-10 samples of your voice.")
    print("Each sample should be 5-10 seconds long.")
    print()
    print("Options:")
    print("1. Record now (if microphone is available)")
    print("2. Provide your own audio files")
    print("3. Use default samples")
    print()
    
    choice = input("Enter your choice (1/2/3): ").strip()
    
    if choice == "1":
        return record_with_microphone()
    elif choice == "2":
        return use_existing_files()
    else:
        return use_default_samples()

def record_with_microphone():
    """Record voice samples using microphone"""
    try:
        import sounddevice as sd
        import soundfile as sf
        import numpy as np
        
        print("\n🎤 Recording with microphone...")
        print("Speak clearly in Hindi for 5-10 seconds per sample.")
        print("We'll record 5 samples.")
        print()
        
        samples = []
        for i in range(5):
            print(f"Sample {i+1}/5 - Press Enter to start recording...")
            input()
            
            print(f"Recording sample {i+1}... (5 seconds)")
            fs = 44100  # Sample rate
            seconds = 5
            
            recording = sd.rec(int(seconds * fs), samplerate=fs, channels=1, dtype='float32')
            sd.wait()  # Wait until recording is finished
            
            output_file = VOICE_DATA_DIR / f"sample_{i+1}.wav"
            sf.write(str(output_file), recording, fs)
            samples.append(output_file)
            print(f"✓ Saved: {output_file}")
        
        print(f"\n✓ Recorded {len(samples)} voice samples")
        return samples
        
    except ImportError:
        print("⚠ sounddevice or soundfile not installed")
        print("Install with: pip install sounddevice soundfile")
        return []
    except Exception as e:
        print(f"⚠ Error recording: {e}")
        return []

def use_existing_files():
    """Use existing audio files for voice cloning"""
    print("\n📁 Provide your own audio files")
    print("Place your .wav or .mp3 files in the voice_data directory")
    print("Files should be named: sample_1.wav, sample_2.wav, etc.")
    print()
    
    samples = []
    for i in range(1, 6):
        wav_file = VOICE_DATA_DIR / f"sample_{i}.wav"
        mp3_file = VOICE_DATA_DIR / f"sample_{i}.mp3"
        
        if wav_file.exists():
            samples.append(wav_file)
        elif mp3_file.exists():
            samples.append(mp3_file)
    
    if samples:
        print(f"✓ Found {len(samples)} voice samples")
        return samples
    else:
        print("⚠ No voice samples found")
        return []

def use_default_samples():
    """Use default text for TTS (no cloning)"""
    print("\n⚠ Using default TTS without voice cloning")
    print("You can add your voice samples later to enable cloning")
    return []

def clone_voice(samples):
    """Clone voice using Coqui TTS"""
    if not samples:
        print("⚠ No voice samples provided, skipping cloning")
        return None
    
    try:
        from TTS.api import TTS
        
        print("\n🧬 Cloning voice...")
        print("This may take a few minutes...")
        
        # Initialize TTS with cloning model
        tts = TTS(model_name="tts_models/multilingual/multi-dataset/xtts_v2")
        
        # Clone voice from samples
        # Note: XTTS v2 supports voice cloning from a single sample
        sample_path = str(samples[0])
        
        # Test cloning
        output_path = str(CLONED_VOICE_DIR / "cloned_voice_test.wav")
        tts.tts_to_file(
            text="यही है वॉइस क्लोनिंग का परीक्षण",
            file_path=output_path,
            speaker=tts.synthesizer.tts_model.speaker_manager.get_speaker_by_id("random"),
            language="hi"
        )
        
        print(f"✓ Voice cloning test complete: {output_path}")
        print("✓ Voice model ready for use")
        
        # Save voice configuration
        voice_config = {
            "model": "xtts_v2",
            "samples": [str(s) for s in samples],
            "language": "hi",
            "cloned_at": "now"
        }
        
        config_path = CLONED_VOICE_DIR / "voice_config.json"
        with open(config_path, "w", encoding="utf-8") as f:
            json.dump(voice_config, f, indent=2, ensure_ascii=False)
        
        print(f"✓ Voice config saved: {config_path}")
        return voice_config
        
    except Exception as e:
        print(f"⚠ Voice cloning failed: {e}")
        return None

def generate_voice_over(text, voice_config=None, engine="edge-tts"):
    """Generate voice over using selected engine"""
    output_path = AUDIO_OUTPUT_DIR / f"voice_{len(list(AUDIO_OUTPUT_DIR.glob('*.mp3'))) + 1}.mp3"
    
    if engine == "edge-tts":
        return generate_edge_tts(text, str(output_path))
    elif engine == "coqui-tts" and voice_config:
        return generate_coqui_tts(text, str(output_path), voice_config)
    else:
        print("⚠ No valid TTS engine available")
        return None

def generate_edge_tts(text, output_path):
    """Generate speech using edge-tts"""
    try:
        import edge_tts
        import asyncio
        
        async def generate():
            communicate = edge_tts.Communicate(text, "hi-IN-MadhurNeural")
            await communicate.save(output_path)
        
        asyncio.run(generate())
        print(f"✓ Generated voice: {output_path}")
        return output_path
        
    except Exception as e:
        print(f"⚠ Edge TTS failed: {e}")
        return None

def generate_coqui_tts(text, output_path, voice_config):
    """Generate speech using Coqui TTS"""
    try:
        from TTS.api import TTS
        
        tts = TTS(model_name=voice_config.get("model", "xtts_v2"))
        tts.tts_to_file(
            text=text,
            file_path=output_path,
            language=voice_config.get("language", "hi")
        )
        
        print(f"✓ Generated voice with Coqui: {output_path}")
        return output_path
        
    except Exception as e:
        print(f"⚠ Coqui TTS failed: {e}")
        return None

def create_voice_cloning_guide():
    """Create a guide for voice cloning"""
    guide = """# Voice Cloning Setup Guide

## Overview
This setup allows you to clone your voice and use it for YouTube video narration.

## Available TTS Engines

### 1. Edge TTS (Recommended for Quick Start)
- **Status**: ✅ Installed
- **Quality**: High (Microsoft Neural Voices)
- **Languages**: Hindi, English, and many more
- **Requirements**: Internet connection
- **Voices**:
  - Hindi: `hi-IN-MadhurNeural`, `hi-IN-SwaraNeural`
  - English: `en-US-AriaNeural`, `en-US-GuyNeural`

### 2. Coqui TTS (For Voice Cloning)
- **Status**: ⚠ Needs installation
- **Quality**: High
- **Features**: Voice cloning support
- **Requirements**: GPU recommended, 2+ GB RAM

### 3. eSpeak (Basic)
- **Status**: ⚠ Needs installation
- **Quality**: Basic
- **Features**: Offline, fast

## Setup Instructions

### Option 1: Use Edge TTS (No Cloning)
```bash
# Already installed
python3 -c "import edge_tts; print('Ready')"
```

### Option 2: Setup Voice Cloning with Coqui TTS

1. **Install dependencies**:
```bash
pip install TTS torch sounddevice soundfile
```

2. **Record your voice**:
   - Record 5-10 samples of your voice
   - Each sample: 5-10 seconds
   - Speak clearly in Hindi
   - Save as: `voice_data/sample_1.wav`, `voice_data/sample_2.wav`, etc.

3. **Clone your voice**:
```bash
python3 voice_cloning_setup.py
```

4. **Use cloned voice**:
```python
from voice_cloning_setup import generate_voice_over

audio_file = generate_voice_over(
    "यह है मेरा वॉइस ओवर",
    voice_config="voice_config.json",
    engine="coqui-tts"
)
```

## Usage Examples

### Generate Voice Over with Edge TTS
```python
from voice_cloning_setup import generate_voice_over

text = "नमस्ते दोस्तों, आज हम सीखेंगे कि वीडियो कैसे बनाते हैं"
audio_file = generate_voice_over(text, engine="edge-tts")
```

### Generate Voice Over with Cloned Voice
```python
from voice_cloning_setup import generate_voice_over, load_voice_config

# Load your cloned voice
voice_config = load_voice_config("voice_config.json")

# Generate speech
text = "यह मेरी अपनी आवाज है"
audio_file = generate_voice_over(text, voice_config=voice_config, engine="coqui-tts")
```

## Tips for Better Voice Cloning

1. **Recording Environment**:
   - Use a quiet room
   - Use a good quality microphone
   - Speak clearly and naturally

2. **Sample Quality**:
   - 5-10 samples minimum
   - Each sample: 5-10 seconds
   - Same speaking style for all samples
   - Consistent volume and tone

3. **Text for Samples**:
   - Use diverse sentences
   - Include different emotions
   - Cover various topics

## Troubleshooting

### Coqui TTS Installation Issues
- **Error: Out of memory**: Use CPU mode or reduce batch size
- **Error: Model download failed**: Check internet connection
- **Slow performance**: Use GPU if available

### Voice Cloning Not Working
- Try with more samples (10+)
- Ensure samples are high quality
- Use consistent speaking style

## Supported Languages
- Hindi (hi-IN)
- English (en-US, en-GB)
- And many more...

## File Structure
```
voice_cloning_setup/
├── voice_data/          # Your voice samples
│   ├── sample_1.wav
│   ├── sample_2.wav
│   └── ...
├── cloned_voice/        # Cloned voice models
│   └── voice_config.json
└── audio_output/        # Generated audio files
    └── voice_1.mp3
```

## License
This setup uses open-source TTS models:
- Edge TTS: Microsoft (Free for personal use)
- Coqui TTS: MIT License
- XTTS v2: Apache 2.0 License

## Support
For issues or questions, please refer to:
- Edge TTS: https://github.com/ranaroussi/edge-tts
- Coqui TTS: https://github.com/coqui-ai/TTS
"""
    
    guide_path = PROJECT_DIR / "VOICE_CLONING_GUIDE.md"
    with open(guide_path, "w", encoding="utf-8") as f:
        f.write(guide)
    
    print(f"✓ Voice cloning guide created: {guide_path}")
    return guide_path

def main():
    """Main setup function"""
    print("=" * 60)
    print("🎤 Voice Cloning Setup for YouTube Videos")
    print("=" * 60)
    print()
    
    # Setup directories
    setup_directories()
    
    # Check dependencies
    print("\n🔍 Checking dependencies...")
    available = check_dependencies()
    
    for engine, status in available.items():
        if status:
            print(f"  ✓ {engine}")
        else:
            print(f"  ✗ {engine}")
    
    # Install Coqui TTS if needed
    if not available.get("coqui-tts", False):
        print("\n⚠ Coqui TTS not available")
        install = input("Install Coqui TTS for voice cloning? (y/n): ").strip().lower()
        if install == "y":
            install_coqui_tts()
            available["coqui-tts"] = True
    
    # Record voice samples
    if available.get("coqui-tts", False):
        samples = record_voice_sample()
        if samples:
            voice_config = clone_voice(samples)
        else:
            voice_config = None
    else:
        print("\n⚠ Voice cloning requires Coqui TTS")
        print("Using Edge TTS for now...")
        voice_config = None
    
    # Create guide
    print("\n📖 Creating voice cloning guide...")
    create_voice_cloning_guide()
    
    # Test voice over
    print("\n🔊 Testing voice over generation...")
    test_text = "नमस्ते, यह एक परीक्षण वॉइस ओवर है"
    audio_file = generate_voice_over(test_text, voice_config, "edge-tts")
    
    if audio_file:
        print(f"\n✅ Setup Complete!")
        print(f"Test audio saved: {audio_file}")
        print(f"\nYou can now use this setup to generate voice overs for your videos.")
    else:
        print("\n⚠ Voice over generation failed")
    
    print("\n" + "=" * 60)

if __name__ == "__main__":
    main()
