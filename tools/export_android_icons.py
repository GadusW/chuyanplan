"""Export approved brand artwork into Android resource and Play listing assets.

Requires Pillow. Run from the repository root: python tools/export_android_icons.py
The input foreground is an image-generated transparent adaptation, not a new logo.
"""
from pathlib import Path
import json
import math
from PIL import Image, ImageDraw, ImageCms

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'assets/android'
RES = OUT / 'res'
LAYER = 1080
SAFE_RADIUS = 300  # More breathing room on OEM circular masks, checked on the supplied phone photo.
OPTICAL_OFFSET = (10, 16)  # px on the 1080px adaptive layer; open upper-right arcs carry less visual mass.

def write(path, text):
    path = OUT / path
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text.strip() + '\n', encoding='utf-8')

def main():
    source = Image.open(ROOT / 'assets/branding/stillune-foreground-source.png').convert('RGBA')
    # Remove near-zero alpha encoder dust, retaining the generated optical placement.
    source.putalpha(source.getchannel('A').point(lambda a: 0 if a <= 2 else a))
    box = source.getchannel('A').getbbox()
    assert box
    symbol = source
    # Fit actual visible pixels, avoiding needless shrinkage from empty corners.
    max_radius = max(math.hypot(i % symbol.width - symbol.width / 2,
                                i // symbol.width - symbol.height / 2)
                     for i, alpha in enumerate(symbol.getchannel('A').tobytes()) if alpha)
    scale = SAFE_RADIUS / max_radius
    size = tuple(max(1, round(n * scale)) for n in symbol.size)
    symbol = symbol.resize(size, Image.Resampling.LANCZOS)
    foreground = Image.new('RGBA', (LAYER, LAYER))
    foreground.alpha_composite(symbol, ((LAYER - size[0]) // 2 + OPTICAL_OFFSET[0],
                                       (LAYER - size[1]) // 2 + OPTICAL_OFFSET[1]))
    bitmap_dir = RES / 'drawable-nodpi'
    bitmap_dir.mkdir(parents=True, exist_ok=True)
    foreground.save(bitmap_dir / 'stillune_foreground.png', optimize=True)
    mono = Image.new('RGBA', foreground.size, 'white')
    mono.putalpha(foreground.getchannel('A'))
    mono.save(bitmap_dir / 'stillune_monochrome.png', optimize=True)

    write('res/drawable/ic_launcher_foreground.xml', '''
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/stillune_foreground" android:gravity="fill" />''')
    write('res/drawable/ic_launcher_monochrome.xml', '''
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/stillune_monochrome" android:gravity="fill" />''')
    write('res/drawable/ic_launcher_background.xml', '''
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <solid android:color="#263451" />
</shape>''')
    for version in [26, 33]:
        mono_line = '\n    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />' if version == 33 else ''
        adaptive = f'''<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />{mono_line}
</adaptive-icon>'''
        for name in ['ic_launcher', 'ic_launcher_round']:
            write(f'res/mipmap-anydpi-v{version}/{name}.xml', adaptive)
    write('res/values/strings.xml', '<resources>\n    <string name="app_name">栖声 · Stillune</string>\n</resources>')
    # Every mask and listing now uses the same calibrated viewport, including pre-adaptive launchers.
    square = Image.new('RGBA', (LAYER, LAYER), '#263451')
    square.alpha_composite(foreground)
    square = square.crop((180, 180, 900, 900)).resize((1024, 1024), Image.Resampling.LANCZOS)
    square.save(ROOT / 'assets/branding/stillune-icon-source-square.png', optimize=True)
    legacy = square.copy()
    brand_mask = Image.new('L', legacy.size)
    ImageDraw.Draw(brand_mask).rounded_rectangle((0, 0, legacy.width - 1, legacy.height - 1), radius=legacy.width * .26, fill=255)
    legacy.putalpha(brand_mask)
    legacy.save(ROOT / 'assets/branding/stillune-icon.png', optimize=True)
    for density, pixels in [('mdpi',48),('hdpi',72),('xhdpi',96),('xxhdpi',144),('xxxhdpi',192)]:
        target = RES / f'mipmap-{density}'
        target.mkdir(parents=True, exist_ok=True)
        # Exact approved rounded artwork for legacy launchers.
        legacy.resize((pixels,pixels),Image.Resampling.LANCZOS).save(target/'ic_launcher.png',optimize=True)
        # A separate round fallback for launchers explicitly requesting roundIcon.
        full = Image.open(ROOT / 'assets/branding/stillune-icon-source-square.png').convert('RGBA')
        full = full.resize((pixels,pixels),Image.Resampling.LANCZOS)
        mask = Image.new('L',(pixels*4,pixels*4))
        ImageDraw.Draw(mask).ellipse((0,0,pixels*4-1,pixels*4-1),fill=255)
        full.putalpha(mask.resize((pixels,pixels),Image.Resampling.LANCZOS))
        full.save(target/'ic_launcher_round.png',optimize=True)
    play = Image.open(ROOT / 'assets/branding/stillune-icon-source-square.png').convert('RGBA')
    play = play.resize((512,512),Image.Resampling.LANCZOS)
    play.putalpha(255)
    profile = ImageCms.ImageCmsProfile(ImageCms.createProfile('sRGB')).tobytes()
    play.save(OUT/'play-store-512.png',icc_profile=profile,optimize=True)
    assert (OUT/'play-store-512.png').stat().st_size <= 1024*1024
    assert play.getchannel('A').getextrema()==(255,255)
    alpha_box=foreground.getchannel('A').getbbox()
    assert alpha_box
    visible_radius = max(math.hypot(i % LAYER - LAYER/2, i // LAYER - LAYER/2)
                         for i, alpha in enumerate(foreground.getchannel('A').tobytes()) if alpha)
    assert visible_radius <= LAYER*33/108
    assert min(alpha_box[2]-alpha_box[0],alpha_box[3]-alpha_box[1]) >= LAYER*48/108
    # Mask previews are QA exports, not launcher source layers.
    previews=[]
    for kind in ['round','rounded-square','squircle','themed']:
        bg=Image.new('RGBA',(LAYER,LAYER),'#263451' if kind!='themed' else '#d8e4e9')
        fg=foreground if kind!='themed' else Image.new('RGBA',foreground.size,'#304650')
        if kind=='themed': fg.putalpha(mono.getchannel('A'))
        bg.alpha_composite(fg)
        # 72dp masked viewport is centered within the 108dp source layer.
        bg=bg.crop((180,180,900,900))
        mask=Image.new('L',(720,720))
        d=ImageDraw.Draw(mask)
        if kind in ['round','themed']: d.ellipse((0,0,719,719),fill=255)
        elif kind=='rounded-square': d.rounded_rectangle((0,0,719,719),radius=216,fill=255)
        else:
            points=[]
            for i in range(720):
                t=i*2*math.pi/720;c=math.cos(t);s=math.sin(t)
                points.append((359.5+359.5*math.copysign(abs(c)**.5,c),359.5+359.5*math.copysign(abs(s)**.5,s)))
            d.polygon(points,fill=255)
        bg.putalpha(mask)
        previews.append((kind,bg))
    for kind,im in previews:
        target=OUT/'previews';target.mkdir(exist_ok=True)
        im.save(target/f'{kind}.png')
    import xml.etree.ElementTree as ET
    for path in RES.rglob('*.xml'): ET.parse(path)
    report={'layer_px':LAYER,'layer_dp':108,'safe_circle_dp':66,'alpha_bbox':alpha_box,
            'visible_radius_px':visible_radius, 'fit_radius_px':SAFE_RADIUS, 'optical_offset_px':OPTICAL_OFFSET,
            'play_bytes':(OUT/'play-store-512.png').stat().st_size,
            'xml_files':len(list(RES.rglob('*.xml'))),'android_build_verified':False}
    (OUT/'export-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps(report))

if __name__=='__main__': main()
