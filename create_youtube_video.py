#!/usr/bin/env python3
"""
Professional YouTube Video Creator
Creates high-quality YouTube videos with voice over, effects, and proper structure
"""

import os
import sys
import json
import time
import asyncio
import subprocess
import numpy as np
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import imageio_ffmpeg

# Configuration
PROJECT_DIR = Path(__file__).parent
FINAL_VIDEO = PROJECT_DIR / "youtube_video_final.mp4"
THUMBNAIL = PROJECT_DIR / "thumbnail_final.png"

# Video specs
WIDTH = 1920
HEIGHT = 1080
FPS = 30
DURATION = 60  # 1 minute

# Colors
COLORS = {
    "bg_dark": (20, 20, 30),
    "bg_light": (35, 35, 50),
    "primary": (240, 240, 245),
    "accent": (0, 212, 170),
    "secondary": (150, 150, 170),
    "red": (255, 60, 60),
    "green": (60, 255, 60),
    "blue": (60, 60, 255),
    "yellow": (255, 200, 60),
    "purple": (180, 60, 255)
}

# Fonts
try:
    font_title = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 110)
    font_subtitle = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 80)
    font_body = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 60)
    font_small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 45)
except:
    font_title = ImageFont.load_default()
    font_subtitle = ImageFont.load_default()
    font_body = ImageFont.load_default()
    font_small = ImageFont.load_default()

# Video script (Professional YouTube style)
SCRIPT = [
    {
        "scene": "intro",
        "start": 0,
        "end": 5,
        "title": "AI Se Video Kaise Banaye",
        "subtitle": "Complete Tutorial",
        "voice": "नमस्ते दोस्तों, स्वागत है हमारे चैनल पर. आज हम सीखेंगे कि AI की मदद से वीडियो कैसे बनाते हैं.",
        "visual": "channel_intro"
    },
    {
        "scene": "step1",
        "start": 5,
        "end": 12,
        "title": "Step 1: Idea & Script",
        "subtitle": "Planning Your Video",
        "voice": "पहला कदम है अपने वीडियो का आइडिया तय करना. फिर उस आइडिया पर एक अच्छा सा स्क्रिप्ट लिखिए. स्क्रिप्ट में हर सीन का डायलॉग क्लियर हो.",
        "visual": "script_writing"
    },
    {
        "scene": "step2",
        "start": 12,
        "end": 20,
        "title": "Step 2: Storyboard",
        "subtitle": "Visual Planning",
        "voice": "स्क्रिप्ट के बाद बारी आती है स्टोरीबोर्ड की. हर सीन का टाइम, कैमरा एंगल, और विजुअल्स डिसाइड करिए. यह आपके वीडियो को प्रोफेशनल बनाता है.",
        "visual": "storyboard"
    },
    {
        "scene": "step3",
        "start": 20,
        "end": 28,
        "title": "Step 3: Generate Assets",
        "subtitle": "AI Power",
        "voice": "अब AI की मदद से इमेजेज, बैकग्राउंड, और एनिमेशन्स जनरेट करिए. आप चाहें तो कोड से भी यह सब कर सकते हैं.",
        "visual": "ai_generation"
    },
    {
        "scene": "step4",
        "start": 28,
        "end": 36,
        "title": "Step 4: Voice Over",
        "subtitle": "Add Narration",
        "voice": "अपने स्क्रिप्ट के हिसाब से वॉइस ओवर रिकॉर्ड करिए या AI TTS से जनरेट करिए. वॉइस क्लियर और ऑडिबल होनी चाहिए.",
        "visual": "voice_over"
    },
    {
        "scene": "step5",
        "start": 36,
        "end": 44,
        "title": "Step 5: Edit & Assemble",
        "subtitle": "Final Touch",
        "voice": "सब एसेट्स को मिला कर वीडियो बनाइए. ट्रांजिशन्स, इफेक्ट्स, और म्यूजिक एड करिए. यह आपका वीडियो और भी अट्रैक्टिव बनाता है.",
        "visual": "video_editing"
    },
    {
        "scene": "step6",
        "start": 44,
        "end": 52,
        "title": "Step 6: QA & Upload",
        "subtitle": "Publish",
        "voice": "वीडियो को चेक करिए. ऑडियो लेवल, वीडियो क्वालिटी, और सबटाइटल्स सही हैं या नहीं. फिर YouTube पर अपलोड करिए.",
        "visual": "upload"
    },
    {
        "scene": "outro",
        "start": 52,
        "end": 60,
        "title": "Subscribe & Like",
        "subtitle": "Support Us",
        "voice": "अगर आपको यह वीडियो पसंद आया, तो लाइक और सब्सक्राइब करना मत भूलिए. और हमें कमेंट में बताइए कि आपको कौन सा टॉपिक चाहिए.",
        "visual": "subscribe"
    }
]

def create_gradient_bg(width, height, color1, color2):
    """Create gradient background"""
    img = Image.new('RGB', (width, height), color1)
    draw = ImageDraw.Draw(img)
    
    for y in range(height):
        ratio = y / height
        r = int(color1[0] + (color2[0] - color1[0]) * ratio)
        g = int(color1[1] + (color2[1] - color1[1]) * ratio)
        b = int(color1[2] + (color2[2] - color1[2]) * ratio)
        draw.line([(0, y), (width, y)], fill=(r, g, b))
    
    return img

def create_scene_frame(scene_data, frame_num, total_frames, scene_progress):
    """Create a frame for a specific scene"""
    img = Image.new('RGB', (WIDTH, HEIGHT), COLORS["bg_dark"])
    draw = ImageDraw.Draw(img)
    
    # Create gradient background
    bg_img = create_gradient_bg(WIDTH, HEIGHT, COLORS["bg_dark"], COLORS["bg_light"])
    img.paste(bg_img, (0, 0))
    
    # Scene-specific visuals
    visual = scene_data["visual"]
    
    if visual == "channel_intro":
        # Channel logo animation
        center_x, center_y = WIDTH // 2, HEIGHT // 2 - 100
        
        # Pulsing AI logo
        pulse_size = 80 + 30 * np.sin(frame_num * 0.1)
        draw.ellipse([
            center_x - pulse_size, center_y - pulse_size,
            center_x + pulse_size, center_y + pulse_size
        ], outline=COLORS["accent"], width=5)
        
        # AI text
        draw.text((center_x - 40, center_y - 30), "AI", fill=COLORS["accent"], font=font_body)
        
        # Channel name
        draw.text((WIDTH//2 - 200, HEIGHT//2 + 50), "Video Production", fill=COLORS["primary"], font=font_subtitle)
        
    elif visual == "script_writing":
        # Notebook/paper effect
        draw.rectangle([
            WIDTH//2 - 250, HEIGHT//2 - 150,
            WIDTH//2 + 250, HEIGHT//2 + 150
        ], fill=(40, 40, 50), outline=COLORS["accent"], width=3)
        
        # Lines
        for i in range(5):
            y = HEIGHT//2 - 130 + i * 50
            draw.line([
                WIDTH//2 - 230, y,
                WIDTH//2 + 230, y
            ], fill=(60, 60, 70), width=2)
        
        # Pen icon
        draw.line([
            WIDTH//2 + 200, HEIGHT//2 - 100,
            WIDTH//2 + 250, HEIGHT//2 - 50
        ], fill=COLORS["accent"], width=4)
        
    elif visual == "storyboard":
        # Storyboard grid
        for i in range(3):
            for j in range(3):
                x = WIDTH//2 - 200 + i * 130
                y = HEIGHT//2 - 150 + j * 100
                draw.rectangle([x, y, x + 110, y + 80], 
                              outline=COLORS["accent"], width=2)
                
                # Add some content
                if i == j:
                    draw.ellipse([x + 20, y + 20, x + 50, y + 50], 
                                fill=COLORS["accent"], outline=COLORS["primary"])
        
        # Arrow connecting boxes
        draw.line([
            WIDTH//2 - 100, HEIGHT//2 - 50,
            WIDTH//2 + 50, HEIGHT//2 - 50
        ], fill=COLORS["accent"], width=3)
        
    elif visual == "ai_generation":
        # AI brain with particles
        center_x, center_y = WIDTH//2, HEIGHT//2
        
        # Brain shape
        brain_points = [
            (center_x, center_y - 80),
            (center_x - 60, center_y - 40),
            (center_x - 80, center_y),
            (center_x - 60, center_y + 40),
            (center_x, center_y + 80),
            (center_x + 60, center_y + 40),
            (center_x + 80, center_y),
            (center_x + 60, center_y - 40)
        ]
        draw.polygon(brain_points, outline=COLORS["accent"], width=3)
        
        # Particles
        for _ in range(80):
            angle = np.random.random() * 2 * np.pi
            distance = 100 + np.random.random() * 200
            x = center_x + distance * np.cos(angle)
            y = center_y + distance * np.sin(angle)
            size = np.random.randint(2, 6)
            alpha = int(200 * np.random.random())
            color = (*COLORS["accent"], alpha)
            draw.ellipse([x - size, y - size, x + size, y + size], 
                        fill=COLORS["accent"])
        
    elif visual == "voice_over":
        # Microphone
        center_x, center_y = WIDTH//2, HEIGHT//2
        
        # Mic body
        draw.ellipse([
            center_x - 40, center_y - 80,
            center_x + 40, center_y + 80
        ], outline=COLORS["primary"], width=4)
        
        # Mic stand
        draw.line([
            center_x, center_y + 80,
            center_x, center_y + 150
        ], fill=COLORS["primary"], width=4)
        
        # Sound waves
        for i in range(5):
            angle = 180 + i * 15
            x = center_x + 80 * np.cos(np.radians(angle))
            y = center_y + 80 * np.sin(np.radians(angle))
            draw.line([center_x, center_y, x, y], 
                     fill=COLORS["accent"], width=3)
        
        # Volume bars
        for i in range(5):
            height = max(10, 30 + int(40 * np.sin(frame_num * 0.5 + i)))
            draw.rectangle([
                WIDTH//2 + 100 + i * 20, HEIGHT//2 - 50,
                WIDTH//2 + 115 + i * 20, HEIGHT//2 - 50 + height
            ], fill=COLORS["green"])
        
    elif visual == "video_editing":
        # Video editor interface
        draw.rectangle([
            WIDTH//2 - 300, HEIGHT//2 - 150,
            WIDTH//2 + 300, HEIGHT//2 + 150
        ], fill=(30, 30, 40), outline=COLORS["primary"], width=3)
        
        # Timeline
        draw.rectangle([
            WIDTH//2 - 280, HEIGHT//2 + 100,
            WIDTH//2 + 280, HEIGHT//2 + 130
        ], fill=(50, 50, 60))
        
        # Playhead
        playhead_x = WIDTH//2 - 280 + int(560 * scene_progress)
        draw.polygon([
            (playhead_x, HEIGHT//2 + 100),
            (playhead_x + 20, HEIGHT//2 + 115),
            (playhead_x, HEIGHT//2 + 130)
        ], fill=COLORS["red"])
        
        # Cut icons
        for i in range(3):
            x = WIDTH//2 - 200 + i * 200
            draw.line([x, HEIGHT//2 + 80, x + 40, HEIGHT//2 + 80], 
                     fill=COLORS["primary"], width=3)
        
    elif visual == "upload":
        # Upload arrow
        center_x, center_y = WIDTH//2, HEIGHT//2
        
        # Cloud
        draw.ellipse([
            center_x - 80, center_y - 40,
            center_x + 80, center_y + 40
        ], fill=(60, 60, 70), outline=COLORS["primary"])
        
        # Upload arrow
        draw.polygon([
            (center_x - 50, center_y + 60),
            (center_x, center_y + 120),
            (center_x + 50, center_y + 60)
        ], fill=COLORS["accent"])
        
        # YouTube logo hint
        draw.polygon([
            (center_x - 30, center_y - 30),
            (center_x + 10, center_y),
            (center_x - 30, center_y + 30)
        ], fill=COLORS["red"])
        
    elif visual == "subscribe":
        # Subscribe button
        center_x, center_y = WIDTH//2, HEIGHT//2
        
        # Button
        draw.rounded_rectangle([
            center_x - 150, center_y - 50,
            center_x + 150, center_y + 50
        ], radius=20, fill=COLORS["red"])
        
        # Text
        draw.text((center_x - 100, center_y - 40), "SUBSCRIBE", 
                 fill=COLORS["primary"], font=font_body)
        
        # Like button
        draw.ellipse([
            WIDTH//2 - 200, center_y - 30,
            WIDTH//2 - 150, center_y + 30
        ], outline=COLORS["primary"], width=3)
        draw.polygon([
            (WIDTH//2 - 190, center_y),
            (WIDTH//2 - 170, center_y + 20),
            (WIDTH//2 - 170, center_y - 20)
        ], fill=COLORS["primary"])
        
        # Bell icon
        draw.ellipse([
            WIDTH//2 + 160, center_y - 30,
            WIDTH//2 + 210, center_y + 30
        ], outline=COLORS["primary"], width=3)
        draw.line([
            WIDTH//2 + 185, center_y + 30,
            WIDTH//2 + 185, center_y + 60
        ], fill=COLORS["primary"], width=3)
    
    # Draw title
    if scene_data["title"]:
        bbox = draw.textbbox((0, 0), scene_data["title"], font=font_title)
        text_width = bbox[2] - bbox[0]
        text_x = (WIDTH - text_width) // 2
        text_y = 120
        draw.text((text_x, text_y), scene_data["title"], fill=COLORS["primary"], font=font_title)
        
        # Underline
        draw.line([
            text_x, text_y + 120,
            text_x + text_width, text_y + 120
        ], fill=COLORS["accent"], width=5)
    
    # Draw subtitle
    if scene_data["subtitle"]:
        bbox = draw.textbbox((0, 0), scene_data["subtitle"], font=font_subtitle)
        text_width = bbox[2] - bbox[0]
        text_x = (WIDTH - text_width) // 2
        text_y = HEIGHT - 180
        draw.text((text_x, text_y), scene_data["subtitle"], fill=COLORS["secondary"], font=font_subtitle)
    
    # Draw progress bar at bottom
    draw.rectangle([
        50, HEIGHT - 30,
        WIDTH - 50, HEIGHT - 20
    ], fill=(40, 40, 50))
    
    progress_width = max(0, int((WIDTH - 100) * scene_progress))
    draw.rectangle([
        50, HEIGHT - 30,
        50 + progress_width, HEIGHT - 20
    ], fill=COLORS["accent"])
    
    # Draw channel watermark
    draw.text((50, 50), "AI Video", fill=COLORS["accent"], font=font_small)
    draw.text((50, 100), "Tutorial", fill=COLORS["secondary"], font=font_small)
    
    return img

def create_all_frames():
    """Create all frames for the video"""
    print("🎬 Creating video frames...")
    
    frames_dir = PROJECT_DIR / "temp_frames"
    frames_dir.mkdir(exist_ok=True)
    
    total_frames = DURATION * FPS
    
    for scene in SCRIPT:
        start_frame = int(scene["start"] * FPS)
        end_frame = int(scene["end"] * FPS)
        scene_duration = scene["end"] - scene["start"]
        
        for frame_num in range(start_frame, end_frame):
            scene_progress = (frame_num - start_frame) / (end_frame - start_frame)
            img = create_scene_frame(scene, frame_num, total_frames, scene_progress)
            img.save(frames_dir / f"frame_{frame_num:04d}.png")
    
    print(f"✓ Created {total_frames} frames")
    return frames_dir

def generate_voice_overs():
    """Generate all voice overs using edge-tts"""
    print("\n🎤 Generating voice overs...")
    
    audio_dir = PROJECT_DIR / "temp_audio"
    audio_dir.mkdir(exist_ok=True)
    
    async def generate_all():
        import edge_tts
        
        for i, scene in enumerate(SCRIPT):
            if scene["voice"]:
                output_file = audio_dir / f"voice_{i:02d}.mp3"
                communicate = edge_tts.Communicate(scene["voice"], "hi-IN-MadhurNeural")
                await communicate.save(str(output_file))
                print(f"  ✓ Generated voice {i+1}/{len(SCRIPT)}")
    
    asyncio.run(generate_all())
    
    # Combine all audio files
    print("\n🎛️ Combining audio files...")
    ffmpeg_path = imageio_ffmpeg.get_ffmpeg_exe()
    audio_list = audio_dir / "file_list.txt"
    combined_audio = audio_dir / "combined.mp3"
    
    with open(audio_list, "w") as f:
        for i in range(len(SCRIPT)):
            f.write(f"file '{audio_dir / f'voice_{i:02d}.mp3'}'\n")
    
    cmd = [
        ffmpeg_path,
        '-y',
        '-f', 'concat',
        '-safe', '0',
        '-i', str(audio_list),
        '-c', 'copy',
        str(combined_audio)
    ]
    
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    
    if result.returncode != 0:
        print(f"⚠ Audio combination warning: {result.stderr}")
    
    return combined_audio

def create_video(frames_dir, audio_file):
    """Create final video with audio"""
    print("\n🎥 Creating final video...")
    
    ffmpeg_path = imageio_ffmpeg.get_ffmpeg_exe()
    temp_video = PROJECT_DIR / "temp_video.mp4"
    
    # Create video from frames
    cmd1 = [
        ffmpeg_path,
        '-y',
        '-framerate', str(FPS),
        '-i', str(frames_dir / 'frame_%04d.png'),
        '-c:v', 'libx264',
        '-pix_fmt', 'yuv420p',
        '-preset', 'fast',
        '-crf', '18',
        '-r', str(FPS),
        str(temp_video)
    ]
    
    result = subprocess.run(cmd1, capture_output=True, text=True, timeout=300)
    
    if result.returncode != 0:
        print(f"⚠ Video creation warning: {result.stderr}")
    else:
        print("✓ Video created from frames")
    
    # Add audio
    if audio_file.exists():
        cmd2 = [
            ffmpeg_path,
            '-y',
            '-i', str(temp_video),
            '-i', str(audio_file),
            '-c:v', 'copy',
            '-c:a', 'aac',
            '-b:a', '192k',
            '-shortest',
            str(FINAL_VIDEO)
        ]
        
        result = subprocess.run(cmd2, capture_output=True, text=True, timeout=60)
        
        if result.returncode != 0:
            print(f"⚠ Audio addition warning: {result.stderr}")
            # Fallback: copy video without audio
            import shutil
            shutil.copy(temp_video, FINAL_VIDEO)
        else:
            print("✓ Audio added to video")
    else:
        import shutil
        shutil.copy(temp_video, FINAL_VIDEO)
        print("⚠ No audio file, video created without audio")
    
    # Cleanup
    import shutil
    if frames_dir.exists():
        shutil.rmtree(frames_dir)
    if (PROJECT_DIR / "temp_audio").exists():
        shutil.rmtree(PROJECT_DIR / "temp_audio")
    if temp_video.exists():
        temp_video.unlink()
    
    print(f"✓ Final video: {FINAL_VIDEO}")
    return FINAL_VIDEO

def create_thumbnail():
    """Create professional thumbnail"""
    print("\n🖼️ Creating thumbnail...")
    
    img = Image.new('RGB', (WIDTH, HEIGHT), COLORS["bg_dark"])
    draw = ImageDraw.Draw(img)
    
    # Gradient background
    bg_img = create_gradient_bg(WIDTH, HEIGHT, COLORS["bg_dark"], COLORS["blue"])
    img.paste(bg_img, (0, 0))
    
    # Title
    title = "AI Se Video Kaise Banaye"
    subtitle = "Complete Tutorial - 1 Minute"
    
    bbox = draw.textbbox((0, 0), title, font=font_title)
    text_width = bbox[2] - bbox[0]
    text_x = (WIDTH - text_width) // 2
    text_y = HEIGHT//2 - 120
    draw.text((text_x, text_y), title, fill=COLORS["accent"], font=font_title)
    
    # Subtitle
    bbox = draw.textbbox((0, 0), subtitle, font=font_subtitle)
    text_width = bbox[2] - bbox[0]
    text_x = (WIDTH - text_width) // 2
    text_y = HEIGHT//2 + 40
    draw.text((text_x, text_y), subtitle, fill=COLORS["primary"], font=font_subtitle)
    
    # AI icon
    center_x, center_y = WIDTH//2, HEIGHT//2 - 180
    draw.ellipse([
        center_x - 70, center_y - 70,
        center_x + 70, center_y + 70
    ], outline=COLORS["accent"], width=6)
    draw.text((center_x - 25, center_y - 25), "AI", fill=COLORS["accent"], font=font_body)
    
    # Play button
    draw.polygon([
        (WIDTH//2 - 50, HEIGHT//2 + 100),
        (WIDTH//2 - 50, HEIGHT//2 + 140),
        (WIDTH//2 + 50, HEIGHT//2 + 120)
    ], fill=COLORS["red"])
    
    # Decorative elements
    for i in range(5):
        x = 100 + i * 300
        draw.ellipse([x, 50, x + 20, 70], fill=COLORS["accent"])
    
    img.save(THUMBNAIL)
    print(f"✓ Thumbnail: {THUMBNAIL}")
    return THUMBNAIL

def create_metadata():
    """Create video metadata"""
    print("\n📝 Creating metadata...")
    
    metadata = {
        "title": "AI Se Video Kaise Banaye - Complete Tutorial",
        "description": "सीखिए कि AI की मदद से प्रोफेशनल वीडियो कैसे बनाते हैं. यह वीडियो आपको स्टेप बाय स्टेप गाइड प्रदान करता है कि कैसे आप AI टूल्स का उपयोग करके अपने YouTube चैनल के लिए उच्च गुणवत्ता वाले वीडियो बना सकते हैं.",
        "tags": [
            "AI Video",
            "Video Production",
            "YouTube Tutorial",
            "Hindi Tutorial",
            "AI Tools",
            "Video Editing",
            "Content Creation",
            "TTS",
            "Text to Speech",
            "AI Voice"
        ],
        "duration": "60 seconds",
        "resolution": "1920x1080",
        "fps": 30,
        "language": "Hindi",
        "created_at": time.strftime("%Y-%m-%d %H:%M:%S"),
        "script": [s.copy() for s in SCRIPT]
    }
    
    metadata_path = PROJECT_DIR / "video_metadata.json"
    with open(metadata_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2, ensure_ascii=False)
    
    print(f"✓ Metadata: {metadata_path}")
    return metadata_path

def main():
    """Main production pipeline"""
    print("=" * 70)
    print("🎬 Professional YouTube Video Production")
    print("=" * 70)
    print()
    
    # Stage 1: Create frames
    print("Stage 1: Creating video frames...")
    frames_dir = create_all_frames()
    print()
    
    # Stage 2: Generate voice overs
    print("Stage 2: Generating voice overs...")
    audio_file = generate_voice_overs()
    print()
    
    # Stage 3: Create video
    print("Stage 3: Creating final video...")
    video_path = create_video(frames_dir, audio_file)
    print()
    
    # Stage 4: Create thumbnail
    print("Stage 4: Creating thumbnail...")
    thumbnail_path = create_thumbnail()
    print()
    
    # Stage 5: Create metadata
    print("Stage 5: Creating metadata...")
    metadata_path = create_metadata()
    print()
    
    # Verify
    if FINAL_VIDEO.exists():
        file_size = FINAL_VIDEO.stat().st_size / (1024 * 1024)
        print("=" * 70)
        print("✅ PRODUCTION COMPLETE!")
        print("=" * 70)
        print(f"📹 Video: {FINAL_VIDEO}")
        print(f"   Size: {file_size:.2f} MB")
        print(f"   Duration: {DURATION} seconds")
        print(f"   Resolution: {WIDTH}x{HEIGHT}")
        print(f"🖼️ Thumbnail: {THUMBNAIL}")
        print(f"📝 Metadata: {metadata_path}")
        print()
        print("✨ Professional YouTube video ready for upload!")
        print("=" * 70)
    else:
        print("❌ Video creation failed!")

if __name__ == "__main__":
    main()
