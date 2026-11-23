#!/usr/bin/env python3
"""Reproducible, transparent WebP brand art for Jibik (no fonts or backgrounds).

The individual assets are deliberate compact 3D-style visual accents, not UI glyphs.
Run: python scripts/generate_jibo_assets.py [optional output directory]
"""
import io
import sys
from pathlib import Path

import cairosvg
from PIL import Image

DEFAULT_OUT = Path('app/src/main/res/drawable-nodpi')
SIZE = 384

DEFS = '''
<defs>
 <linearGradient id="green" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#4BC994"/><stop offset=".42" stop-color="#118C6B"/><stop offset="1" stop-color="#064B42"/></linearGradient>
 <linearGradient id="dark" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#197F64"/><stop offset=".6" stop-color="#075449"/><stop offset="1" stop-color="#04382F"/></linearGradient>
 <linearGradient id="gold" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#FFF0A8"/><stop offset=".42" stop-color="#F8C44E"/><stop offset="1" stop-color="#DC8F19"/></linearGradient>
 <linearGradient id="orange" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#FFC270"/><stop offset=".35" stop-color="#FF9845"/><stop offset="1" stop-color="#DB641E"/></linearGradient>
 <linearGradient id="cream" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#FFFFFF"/><stop offset="1" stop-color="#E6E5D2"/></linearGradient>
 <linearGradient id="mint" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#BFF5C9"/><stop offset="1" stop-color="#60CFA7"/></linearGradient>
 <linearGradient id="paper" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#FFFFFF"/><stop offset="1" stop-color="#F0EADA"/></linearGradient>
</defs>
'''

def svg(*parts):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 384 384" '
            'width="384" height="384">' + DEFS +
            '<ellipse cx="192" cy="329" rx="121" ry="17" fill="#154F47" opacity=".11"/>'
            + ''.join(parts) + '</svg>')

FOX = '''
<g transform="translate(16 14)">
 <path d="M110 256 Q40 251 45 190 Q55 134 87 152 Q58 190 101 207 L125 199Z" fill="url(#orange)" stroke="#B95B27" stroke-width="5"/>
 <path d="M47 185 Q54 228 95 238 Q72 240 59 226Z" fill="#FFF7EA"/>
 <rect x="101" y="220" width="136" height="104" rx="55" fill="url(#dark)" stroke="#064F43" stroke-width="5"/>
 <path d="M127 253 Q166 274 211 251" fill="none" stroke="#71CEA0" stroke-width="6" stroke-linecap="round" opacity=".7"/>
 <path d="M106 101 Q112 40 130 23 Q169 33 178 101Z" fill="url(#orange)" stroke="#C76522" stroke-width="6"/>
 <path d="M128 82 L133 50 L155 83Z" fill="#F4A78A"/>
 <path d="M214 102 Q218 44 248 24 Q265 52 264 110Z" fill="url(#orange)" stroke="#C76522" stroke-width="6"/>
 <path d="M232 86 L247 53 L250 91Z" fill="#F6AC92"/>
 <path d="M102 128 Q98 86 160 79 Q234 63 275 121 Q289 173 254 222 Q204 266 136 232 Q95 206 102 128Z" fill="url(#orange)" stroke="#CF702A" stroke-width="5"/>
 <path d="M107 168 Q123 197 145 192 Q147 214 174 218 Q174 238 147 229 Q113 215 107 168Z" fill="#FFF3DD"/>
 <path d="M274 160 Q263 199 233 200 Q229 230 199 225 Q193 244 235 228 Q267 218 274 160Z" fill="#FFF5E2"/>
 <ellipse cx="186" cy="199" rx="51" ry="35" fill="url(#cream)"/>
 <ellipse cx="158" cy="147" rx="12" ry="16" fill="#233833"/>
 <ellipse cx="234" cy="145" rx="12" ry="16" fill="#233833"/>
 <circle cx="163" cy="142" r="4" fill="white"/><circle cx="239" cy="140" r="4" fill="white"/>
 <path d="M174 194 Q187 179 199 195 Q190 205 184 205Z" fill="#49342D"/>
 <path d="M182 210 Q189 217 198 210" fill="none" stroke="#6A4B3B" stroke-width="4" stroke-linecap="round"/>
 <path d="M135 119 Q154 109 169 118" fill="none" stroke="#9B4B23" stroke-width="5" stroke-linecap="round"/>
 <path d="M219 116 Q236 108 253 117" fill="none" stroke="#9B4B23" stroke-width="5" stroke-linecap="round"/>
</g>
'''

def coin(x=0,y=0,scale=1):
    return f'''<g transform="translate({x} {y}) scale({scale})">
    <circle cx="290" cy="289" r="52" fill="url(#gold)" stroke="#BC801E" stroke-width="7"/>
    <circle cx="290" cy="289" r="39" fill="none" stroke="#FFECA1" stroke-width="5" opacity=".9"/>
    <path d="M279 291 Q290 276 302 289 Q293 301 281 307" fill="none" stroke="#B98224" stroke-width="5" stroke-linecap="round"/>
    </g>'''

WALLET = '''
<g transform="translate(16 42)">
 <rect x="80" y="117" width="242" height="155" rx="31" fill="url(#dark)" stroke="#064A3E" stroke-width="6"/>
 <rect x="104" y="91" width="183" height="94" rx="16" fill="url(#gold)" stroke="#E3A63A" stroke-width="5" transform="rotate(-8 104 91)"/>
 <rect x="106" y="105" width="195" height="82" rx="17" fill="url(#cream)" stroke="#CFD2BB" stroke-width="4" transform="rotate(4 106 105)"/>
 <rect x="75" y="148" width="245" height="149" rx="29" fill="url(#green)" stroke="#075D4F" stroke-width="5"/>
 <path d="M98 167 H285" stroke="#B8F2CE" stroke-width="7" opacity=".7" stroke-linecap="round"/>
 <rect x="224" y="194" width="113" height="58" rx="22" fill="url(#dark)" stroke="#C7E5C7" stroke-width="4"/>
 <circle cx="282" cy="223" r="12" fill="url(#gold)"/>
</g>'''

RECEIPT = '''
<g transform="translate(0 0)">
 <path d="M129 51 H270 Q284 51 284 65 V309 L264 297 L243 309 L221 297 L201 309 L179 297 L159 309 L138 297 L120 309 V65 Q120 51 129 51Z" fill="url(#paper)" stroke="#D1CBAF" stroke-width="5"/>
 <path d="M157 102 H248 M157 129 H235 M157 156 H246 M157 185 H208" stroke="#C0C9B8" stroke-width="12" stroke-linecap="round"/>
 <circle cx="216" cy="250" r="32" fill="url(#green)"/>
 <path d="M202 249 L214 262 L235 236" fill="none" stroke="white" stroke-width="9" stroke-linecap="round" stroke-linejoin="round"/>
</g>'''

PIGGY = '''
<g transform="translate(6 12)">
 <ellipse cx="195" cy="210" rx="129" ry="98" fill="url(#mint)" stroke="#52A97C" stroke-width="6"/>
 <path d="M114 152 Q97 103 137 114 L160 132" fill="url(#mint)" stroke="#4EA779" stroke-width="6"/>
 <path d="M230 142 Q274 97 289 127 L272 151" fill="url(#mint)" stroke="#4EA779" stroke-width="6"/>
 <ellipse cx="304" cy="226" rx="40" ry="32" fill="url(#green)" stroke="#2E9F79" stroke-width="5"/>
 <circle cx="296" cy="221" r="6" fill="#075D4C"/><circle cx="314" cy="221" r="6" fill="#075D4C"/>
 <circle cx="172" cy="183" r="7" fill="#1D5F4B"/><path d="M236 177 Q248 187 260 175" fill="none" stroke="#246B58" stroke-width="6" stroke-linecap="round"/>
 <rect x="122" y="280" width="43" height="26" rx="13" fill="url(#green)"/>
 <rect x="241" y="276" width="43" height="28" rx="13" fill="url(#green)"/>
 <rect x="158" y="133" width="72" height="9" rx="4" fill="#40765C" transform="rotate(-9 158 133)"/>
 <ellipse cx="190" cy="101" rx="31" ry="10" fill="url(#gold)" stroke="#C28D27" stroke-width="4"/>
 <rect x="162" y="71" width="57" height="30" rx="3" fill="url(#gold)"/>
 <ellipse cx="190" cy="71" rx="29" ry="10" fill="url(#gold)"/>
</g>'''

CHART = '''
<g transform="translate(22 18)">
 <rect x="52" y="200" width="58" height="107" rx="15" fill="url(#dark)" stroke="#064D44" stroke-width="4"/>
 <rect x="128" y="152" width="58" height="155" rx="15" fill="url(#green)" stroke="#188D6D" stroke-width="4"/>
 <rect x="204" y="103" width="58" height="204" rx="15" fill="url(#mint)" stroke="#4AB78D" stroke-width="4"/>
 <rect x="280" y="59" width="44" height="248" rx="14" fill="url(#gold)" stroke="#D7A146" stroke-width="4"/>
 <path d="M49 184 L147 131 L224 104 L298 46" fill="none" stroke="url(#gold)" stroke-width="14" stroke-linecap="round" stroke-linejoin="round"/>
 <path d="M259 45 L301 40 L290 81" fill="none" stroke="#C68D22" stroke-width="13" stroke-linecap="round" stroke-linejoin="round"/>
</g>'''

TARGET = '''
<circle cx="212" cy="190" r="119" fill="url(#gold)" stroke="#CD932F" stroke-width="7"/>
<circle cx="212" cy="190" r="91" fill="url(#cream)" stroke="#D9BA72" stroke-width="5"/>
<circle cx="212" cy="190" r="63" fill="url(#gold)"/>
<circle cx="212" cy="190" r="37" fill="url(#cream)"/>
<circle cx="212" cy="190" r="15" fill="url(#green)"/>
<path d="M218 187 L315 89" stroke="#075F4D" stroke-width="13" stroke-linecap="round"/>
<path d="M316 84 L335 36 L310 49 L297 24 L289 70Z" fill="url(#green)" stroke="#08634F" stroke-width="4"/>
'''

CALENDAR = '''
<g transform="translate(20 14)">
 <rect x="57" y="77" width="256" height="227" rx="29" fill="url(#cream)" stroke="#D4CDAF" stroke-width="5"/>
 <path d="M57 127 V104 Q57 77 83 77 H286 Q313 77 313 104 V127Z" fill="url(#dark)"/>
 <path d="M114 52 V101 M257 52 V101" stroke="url(#gold)" stroke-width="18" stroke-linecap="round"/>
 <g fill="#B4C8B6"><rect x="91" y="159" width="37" height="34" rx="9"/><rect x="147" y="159" width="37" height="34" rx="9"/><rect x="204" y="159" width="37" height="34" rx="9"/><rect x="91" y="213" width="37" height="34" rx="9"/><rect x="147" y="213" width="37" height="34" rx="9"/></g>
 <rect x="204" y="211" width="41" height="39" rx="10" fill="url(#orange)"/>
 <circle cx="277" cy="269" r="56" fill="url(#gold)" stroke="#BE8734" stroke-width="5"/>
 <path d="M246 281 Q262 260 265 242 Q276 219 290 242 Q296 262 312 281Z" fill="#FDF1D2"/>
 <circle cx="277" cy="286" r="7" fill="#AC741F"/>
</g>'''

BASKET = '''
<g transform="translate(12 0)">
 <path d="M87 166 L118 303 Q126 328 161 328 H264 Q294 328 302 303 L323 166Z" fill="url(#green)" stroke="#0A6D57" stroke-width="6"/>
 <path d="M144 165 Q172 61 247 165" fill="none" stroke="url(#gold)" stroke-width="20" stroke-linecap="round"/>
 <ellipse cx="142" cy="145" rx="48" ry="61" fill="url(#mint)"/>
 <ellipse cx="194" cy="144" rx="38" ry="66" fill="url(#green)"/>
 <ellipse cx="244" cy="141" rx="46" ry="64" fill="url(#mint)"/>
 <circle cx="271" cy="154" r="43" fill="url(#orange)"/>
 <path d="M266 122 Q269 95 298 98" fill="none" stroke="#13764F" stroke-width="13" stroke-linecap="round"/>
 <rect x="76" y="159" width="260" height="28" rx="13" fill="url(#gold)" stroke="#CB983A" stroke-width="5"/>
 <path d="M129 207 L141 296 M179 206 L188 304 M233 207 L229 302 M281 207 L266 296" stroke="#B7F0C6" stroke-width="11" opacity=".54"/>
</g>'''

FOLDERS = '''
<g transform="translate(16 16)">
 <path d="M49 123 Q49 99 75 99 H153 L178 122 H307 Q329 122 329 146 V294 Q329 316 305 316 H74 Q49 316 49 290Z" fill="url(#gold)" stroke="#DAA444" stroke-width="5"/>
 <path d="M60 111 Q60 87 86 87 H153 L173 112 H298 Q319 112 319 136 V283 Q319 304 298 304 H81 Q60 304 60 280Z" fill="url(#cream)" stroke="#E4D9BC" stroke-width="4"/>
 <path d="M46 147 Q46 126 69 126 H146 L170 150 H306 Q330 150 330 174 V306 Q330 328 306 328 H71 Q46 328 46 305Z" fill="url(#dark)" stroke="#07594C" stroke-width="6"/>
 <circle cx="250" cy="241" r="51" fill="url(#cream)"/>
 <g fill="url(#green)"><rect x="222" y="214" width="23" height="23" rx="5"/><rect x="253" y="214" width="23" height="23" rx="5"/><rect x="222" y="244" width="23" height="23" rx="5"/><rect x="253" y="244" width="23" height="23" rx="5"/></g>
</g>'''

MAGNIFIER = '''
<g transform="translate(4 0)">
 <circle cx="250" cy="213" r="64" fill="url(#cream)" stroke="url(#gold)" stroke-width="17"/>
 <path d="M287 256 L337 310" stroke="url(#gold)" stroke-width="21" stroke-linecap="round"/>
 <path d="M235 211 Q255 191 277 211" fill="none" stroke="#CDA976" stroke-width="8" stroke-linecap="round"/>
</g>'''

BELL = '''
<g transform="translate(14 10)">
 <path d="M102 276 Q126 239 128 184 Q130 118 191 110 Q257 111 258 185 Q258 243 281 276Z" fill="url(#cream)" stroke="#DCCAA8" stroke-width="7"/>
 <path d="M116 283 Q194 297 271 283" fill="none" stroke="url(#gold)" stroke-width="18" stroke-linecap="round"/>
 <path d="M164 302 Q191 328 218 300" fill="url(#gold)" stroke="#C38C23" stroke-width="5"/>
 <path d="M184 111 Q176 89 191 81 Q212 89 200 111" fill="url(#gold)"/>
 <path d="M65 172 Q44 191 55 221 M318 172 Q345 196 332 221" fill="none" stroke="url(#green)" stroke-width="13" stroke-linecap="round"/>
 <path d="M285 101 L295 70 L311 96 L337 100 L315 117 L312 142 L293 127 L269 134Z" fill="url(#gold)"/>
</g>'''

STAR = '''
<path d="M300 52 L314 91 L355 100 L323 125 L326 168 L295 143 L255 160 L266 120 L241 91 L283 89Z" fill="url(#gold)" stroke="#CD933A" stroke-width="5"/>
<path d="M278 164 L337 229" stroke="#0E785E" stroke-width="17" stroke-linecap="round"/>
<circle cx="253" cy="64" r="10" fill="url(#mint)"/><circle cx="344" cy="61" r="8" fill="url(#orange)"/>
'''

def small_fox():
    return '<g transform="translate(-26 15) scale(.82)">' + FOX + '</g>'

ASSETS = {
    'home': svg(small_fox(), '<g transform="translate(115 151) scale(.60)">' + WALLET + '</g>'),
    'transactions': svg(small_fox(), '<g transform="translate(158 110) scale(.61)">' + RECEIPT + '</g>'),
    'budget': svg(PIGGY),
    'reports': svg(CHART),
    'accounts': svg(WALLET,coin(-20,0,.33)),
    'goals': svg('<g transform="translate(28 38) scale(.70)">'+FOX+'</g>', '<g transform="translate(134 138) scale(.65)">'+TARGET+'</g>'),
    'obligations': svg(CALENDAR),
    'purchase': svg(BASKET),
    'categories': svg(FOLDERS),
    'jibo_ai': svg(small_fox(), STAR),
    'empty': svg(small_fox(),MAGNIFIER),
    'reminders': svg(BELL),
}

def render(target: Path):
    target.mkdir(parents=True,exist_ok=True)
    report=[]
    for key, source in ASSETS.items():
        png=cairosvg.svg2png(bytestring=source.encode(), output_width=SIZE*2, output_height=SIZE*2)
        im=Image.open(io.BytesIO(png)).convert('RGBA')
        im=im.resize((SIZE,SIZE),Image.Resampling.LANCZOS)
        name='jibo_art_'+key+'.webp'
        path=target/name
        im.save(path, 'WEBP', quality=78, method=6)
        if Image.open(path).getchannel('A').getpixel((0,0))!=0:
            raise AssertionError(f'Asset has background: {name}')
        report.append((name,path.stat().st_size))
    for name,length in report:
        print(f'{name}: {length:,} bytes')
    print(f'Assets: {len(report)}, combined size: {sum(x[1] for x in report):,} bytes')

if __name__ == '__main__':
    render(Path(sys.argv[1]) if len(sys.argv)>1 else DEFAULT_OUT)
