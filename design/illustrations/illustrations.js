// Noodpakket product illustrations: one per catalog item, 120x120, soft flat-3D style.
// Used by the design mockups and exported to SVG for Xcode (asset catalog, vector) and Android (VectorDrawable).
(function (root) {
  const g = (id, a, b, x2 = 0, y2 = 1) =>
    `<linearGradient id="${id}" x1="0" y1="0" x2="${x2}" y2="${y2}"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${b}"/></linearGradient>`;
  const shadow = (cx = 60, w = 34) => `<ellipse cx="${cx}" cy="106" rx="${w}" ry="5" fill="#0B2A1A" opacity=".14"/>`;

  const ART = {
    water: `<defs>${g('wa1', '#7CC8FF', '#2A7FE0', 1, 0)}${g('wa2', '#2A7FE0', '#1B5FB8')}</defs>${shadow(60, 30)}
      <rect x="52" y="10" width="16" height="10" rx="3" fill="#1B5FB8"/>
      <path d="M50 20h20v8c8 4 12 10 12 18v52c0 5-4 8-8 8H46c-4 0-8-3-8-8V46c0-8 4-14 12-18z" fill="url(#wa1)"/>
      <path d="M38 58h44v18H38z" fill="#fff" opacity=".9"/><path d="M45 64h22M45 70h14" stroke="#2A7FE0" stroke-width="3" stroke-linecap="round"/>
      <path d="M46 34c-3 3-4 7-4 12v6" stroke="#fff" stroke-width="4" stroke-linecap="round" opacity=".55"/>
      <path d="M38 86h44" stroke="#1B5FB8" stroke-width="2" opacity=".35"/>`,
    food: `<defs>${g('fo1', '#E9EDF1', '#AEB7C1', 1, 0)}${g('fo2', '#FF6B5E', '#D93A3A', 1, 0)}${g('fo3', '#FFC24B', '#F09A12', 1, 0)}</defs>${shadow(60, 40)}
      <rect x="20" y="44" width="40" height="58" rx="6" fill="url(#fo1)"/><rect x="20" y="56" width="40" height="34" fill="url(#fo2)"/>
      <ellipse cx="40" cy="44" rx="20" ry="5" fill="#CED5DC"/><ellipse cx="40" cy="44" rx="15" ry="3" fill="#B5BEC8"/>
      <circle cx="40" cy="73" r="9" fill="#fff" opacity=".92"/><path d="M36 73h8M40 69v8" stroke="#D93A3A" stroke-width="3" stroke-linecap="round"/>
      <rect x="62" y="30" width="38" height="72" rx="6" fill="url(#fo3)"/><rect x="62" y="30" width="38" height="14" rx="6" fill="#E08A0B"/>
      <path d="M70 58h22M70 66h16M70 74h20" stroke="#fff" stroke-width="3.5" stroke-linecap="round" opacity=".85"/>
      <path d="M24 60v26" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".35"/>`,
    medicines: `<defs>${g('me1', '#FFFFFF', '#E3E8EE')}${g('me2', '#35C27E', '#1B9A5E')}</defs>${shadow(60, 38)}
      <rect x="22" y="36" width="62" height="64" rx="8" fill="url(#me1)" stroke="#D3DAE2" stroke-width="2"/>
      <rect x="22" y="36" width="62" height="16" rx="8" fill="url(#me2)"/><rect x="22" y="46" width="62" height="6" fill="#1B9A5E"/>
      <path d="M53 64v24M41 76h24" stroke="#1B9A5E" stroke-width="8" stroke-linecap="round"/>
      <g transform="rotate(-28 88 70)"><rect x="74" y="58" width="30" height="14" rx="7" fill="#FF8A3D"/><rect x="89" y="58" width="15" height="14" rx="7" fill="#fff"/><rect x="89" y="58" width="5" height="14" fill="#fff"/></g>`,
    firstaid: `<defs>${g('fa1', '#FF6B5E', '#D6342F')}</defs>${shadow(60, 42)}
      <path d="M46 30v-6c0-3 2-5 5-5h18c3 0 5 2 5 5v6" stroke="#B42A26" stroke-width="6" fill="none"/>
      <rect x="16" y="30" width="88" height="70" rx="12" fill="url(#fa1)"/>
      <rect x="16" y="54" width="88" height="5" fill="#B42A26" opacity=".35"/>
      <path d="M60 50v30M45 65h30" stroke="#fff" stroke-width="11" stroke-linecap="round"/>
      <path d="M24 40h20" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".4"/>`,
    flashlight: `<defs>${g('fl1', '#FFE27A', '#F5B800', 1, 0)}${g('fl2', '#FFF3B0', '#FFF3B0')}<radialGradient id="flb" cx=".1" cy=".5" r="1"><stop offset="0" stop-color="#FFE98A" stop-opacity=".9"/><stop offset="1" stop-color="#FFE98A" stop-opacity="0"/></radialGradient></defs>${shadow(52, 36)}
      <path d="M78 42l38-22v76L78 74z" fill="url(#flb)"/>
      <g transform="rotate(-12 50 60)">
      <rect x="10" y="50" width="54" height="22" rx="8" fill="url(#fl1)"/>
      <path d="M62 44h16v34H62z" fill="#3A4652"/><rect x="76" y="40" width="8" height="42" rx="3" fill="#56636F"/>
      <rect x="82" y="44" width="4" height="34" rx="2" fill="#FFF7C2"/>
      <rect x="28" y="56" width="12" height="10" rx="3" fill="#3A4652"/><path d="M14 55h40" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".5"/></g>`,
    batteries: `<defs>${g('ba1', '#3A3F46', '#1E2227', 1, 0)}${g('ba2', '#FFB23E', '#E8870C', 1, 0)}</defs>${shadow(60, 40)}
      <g transform="rotate(-8 40 66)"><rect x="24" y="30" width="28" height="72" rx="6" fill="url(#ba1)"/><rect x="24" y="30" width="28" height="26" rx="6" fill="url(#ba2)"/><rect x="24" y="50" width="28" height="6" fill="#E8870C"/><rect x="32" y="24" width="12" height="7" rx="2" fill="#B8C0C8"/>
      <path d="M33 74h10M38 69v10" stroke="#fff" stroke-width="3" stroke-linecap="round"/></g>
      <g transform="rotate(10 78 66)"><rect x="64" y="32" width="28" height="70" rx="6" fill="url(#ba1)"/><rect x="64" y="32" width="28" height="26" rx="6" fill="url(#ba2)"/><rect x="64" y="52" width="28" height="6" fill="#E8870C"/><rect x="72" y="26" width="12" height="7" rx="2" fill="#B8C0C8"/>
      <path d="M73 76h10M78 71v10" stroke="#fff" stroke-width="3" stroke-linecap="round"/></g>`,
    powerbank: `<defs>${g('pb1', '#5B6774', '#2E3640', 1, 1)}</defs>${shadow(60, 36)}
      <rect x="28" y="20" width="64" height="84" rx="14" fill="url(#pb1)"/>
      <rect x="34" y="26" width="52" height="72" rx="10" fill="none" stroke="#fff" stroke-opacity=".12" stroke-width="2"/>
      <path d="M64 40l-14 22h11l-4 18 14-24H60z" fill="#46E08F"/>
      <g fill="#46E08F"><rect x="42" y="88" width="8" height="4" rx="2"/><rect x="53" y="88" width="8" height="4" rx="2"/><rect x="64" y="88" width="8" height="4" rx="2"/><rect x="75" y="88" width="8" height="4" rx="2" opacity=".3"/></g>
      <rect x="50" y="15" width="20" height="7" rx="2" fill="#2E3640"/>`,
    candles: `<defs>${g('ca1', '#FFFFFF', '#E6E2D8', 1, 0)}${g('ca2', '#FFE26A', '#FF8A1E')}${g('ca3', '#4E7DE0', '#2B55B5')}</defs>${shadow(60, 40)}
      <rect x="26" y="46" width="18" height="56" rx="4" fill="url(#ca1)"/><rect x="48" y="36" width="18" height="66" rx="4" fill="url(#ca1)"/>
      <path d="M35 46v-6M57 36v-6" stroke="#3A3A3A" stroke-width="2"/>
      <path d="M35 40c-5-6 0-12 0-16 4 5 6 10 0 16z" fill="url(#ca2)"/><path d="M57 30c-5-6 0-12 0-16 4 5 6 10 0 16z" fill="url(#ca2)"/>
      <rect x="70" y="72" width="34" height="30" rx="4" fill="url(#ca3)"/><rect x="70" y="72" width="34" height="9" rx="3" fill="#C9D6F5"/>
      <path d="M78 66l14-6" stroke="#E8C08A" stroke-width="3" stroke-linecap="round"/><circle cx="93" cy="59.5" r="3.5" fill="#D93A3A"/>`,
    radio: `<defs>${g('ra1', '#FF9A4D', '#E8661B')}</defs>${shadow(60, 44)}
      <path d="M78 36L96 12" stroke="#56636F" stroke-width="4" stroke-linecap="round"/><circle cx="96" cy="12" r="4" fill="#56636F"/>
      <rect x="14" y="36" width="92" height="64" rx="12" fill="url(#ra1)"/>
      <rect x="22" y="46" width="44" height="46" rx="8" fill="#3A4652"/>
      <g fill="#56636F">${[0, 1, 2, 3].map(r => [0, 1, 2, 3].map(c => `<circle cx="${30 + c * 10}" cy="${54 + r * 10}" r="3"/>`).join('')).join('')}</g>
      <rect x="72" y="46" width="26" height="14" rx="4" fill="#FFE7C9"/><path d="M76 53h18" stroke="#E8661B" stroke-width="2"/><path d="M84 47v12" stroke="#D93A3A" stroke-width="2"/>
      <circle cx="85" cy="78" r="10" fill="#C24F10"/><circle cx="85" cy="78" r="5" fill="#FFB27A"/>`,
    cash: `<defs>${g('cs1', '#7EDC9E', '#2FA864', 1, 1)}${g('cs2', '#FFD66B', '#E5A400')}</defs>${shadow(56, 42)}
      <g transform="rotate(-10 56 60)"><rect x="14" y="38" width="80" height="44" rx="6" fill="#1F8A50"/></g>
      <g transform="rotate(-3 56 64)"><rect x="16" y="44" width="80" height="44" rx="6" fill="url(#cs1)"/>
      <circle cx="56" cy="66" r="11" fill="none" stroke="#fff" stroke-width="3" opacity=".85"/><text x="56" y="71" font-family="Arial, sans-serif" font-weight="700" font-size="14" fill="#fff" text-anchor="middle">€</text>
      <path d="M24 52h10M78 80h10" stroke="#fff" stroke-width="3" stroke-linecap="round" opacity=".6"/></g>
      <ellipse cx="92" cy="98" rx="14" ry="5" fill="#C78C00"/><rect x="78" y="88" width="28" height="10" fill="#C78C00"/><ellipse cx="92" cy="88" rx="14" ry="5" fill="url(#cs2)"/>`,
    documents: `<defs>${g('do1', '#3F66C8', '#26449A', 1, 1)}</defs>${shadow(60, 40)}
      <g transform="rotate(8 70 62)"><rect x="50" y="22" width="52" height="70" rx="5" fill="#fff" stroke="#D3DAE2" stroke-width="2"/>
      <path d="M58 36h36M58 46h30M58 56h34M58 66h22" stroke="#B7C2CE" stroke-width="3" stroke-linecap="round"/></g>
      <rect x="18" y="32" width="54" height="72" rx="7" fill="url(#do1)"/>
      <circle cx="45" cy="62" r="13" fill="none" stroke="#E8C66A" stroke-width="3"/><path d="M32 62h26M45 49c-6 8-6 18 0 26M45 49c6 8 6 18 0 26" stroke="#E8C66A" stroke-width="2" fill="none"/>
      <path d="M32 88h26" stroke="#E8C66A" stroke-width="3" stroke-linecap="round"/>`,
    blankets: `<defs>${g('bl1', '#2BB5A0', '#17877A')}${g('bl2', '#F06D5E', '#C8473B')}</defs>${shadow(60, 44)}
      <rect x="18" y="74" width="84" height="26" rx="8" fill="url(#bl1)"/><path d="M38 74v26M62 74v26M86 74v26M18 87h84" stroke="#fff" stroke-width="2.5" opacity=".35"/>
      <rect x="22" y="50" width="76" height="26" rx="8" fill="url(#bl2)"/><path d="M40 50v26M60 50v26M80 50v26M22 63h76" stroke="#fff" stroke-width="2.5" opacity=".35"/>
      <rect x="28" y="28" width="64" height="24" rx="8" fill="#F3E3C3"/><path d="M44 28v24M60 28v24M76 28v24M28 40h64" stroke="#C9A86A" stroke-width="2.5" opacity=".6"/>`,
    hygiene: `<defs>${g('hy1', '#FFFFFF', '#E4E9EE', 1, 0)}${g('hy2', '#4B5560', '#262C33')}</defs>${shadow(60, 42)}
      <path d="M72 44c0-10 20-10 20-2l6 6c4 4 6 10 6 18v28c0 4-3 6-6 6H70c-3 0-6-2-6-6V66c0-8 2-14 6-18z" fill="url(#hy2)"/>
      <path d="M80 40c2-6 8-8 12-4" stroke="#FFC24B" stroke-width="3" fill="none" stroke-linecap="round"/>
      <rect x="16" y="46" width="48" height="56" rx="8" fill="url(#hy1)" stroke="#D3DAE2" stroke-width="2"/>
      <ellipse cx="40" cy="46" rx="24" ry="8" fill="#F7F9FB" stroke="#D3DAE2" stroke-width="2"/><ellipse cx="40" cy="46" rx="8" ry="3" fill="#B7C2CE"/>
      <path d="M16 74c8 4 16 4 24 0s16-4 24 0" stroke="#D3DAE2" stroke-width="2" fill="none"/>`,
    petfood: `<defs>${g('pf1', '#5AA9FF', '#2E78D6')}${g('pf2', '#C98A4B', '#9A6232')}</defs>${shadow(60, 44)}
      <g fill="url(#pf2)"><circle cx="40" cy="60" r="7"/><circle cx="54" cy="56" r="7"/><circle cx="68" cy="58" r="7"/><circle cx="80" cy="62" r="7"/><circle cx="48" cy="66" r="7"/><circle cx="62" cy="65" r="7"/><circle cx="75" cy="68" r="6"/></g>
      <path d="M16 66h88l-8 30c-1 4-4 6-8 6H32c-4 0-7-2-8-6z" fill="url(#pf1)"/>
      <path d="M16 66h88" stroke="#1F5DB0" stroke-width="4" stroke-linecap="round"/>
      <g fill="#fff" opacity=".9"><circle cx="60" cy="86" r="6"/><circle cx="50" cy="78" r="3"/><circle cx="57" cy="75" r="3"/><circle cx="64" cy="75" r="3"/><circle cx="71" cy="78" r="3"/></g>`,
    // used for custom items and scanned products without a photo
    other: `<defs>${g('ot1', '#D9C29A', '#B8935B', 1, 1)}</defs>${shadow(60, 40)}
      <path d="M20 44l40-16 40 16v48l-40 14-40-14z" fill="url(#ot1)"/><path d="M20 44l40 16 40-16M60 60v46" stroke="#8F6E3E" stroke-width="2.5" fill="none"/>
      <path d="M40 36l40 16v14" stroke="#F3E3C3" stroke-width="6" fill="none"/>`,
  };

  // category look: tile gradient and accent
  const CAT = {
    waterFood: { a: '#E6F3FF', b: '#CFE6FF', ink: '#1B5FB8' },
    health: { a: '#FFECEC', b: '#FFD6D3', ink: '#C8372F' },
    lightPower: { a: '#FFF6D6', b: '#FFE9A8', ink: '#B37A00' },
    information: { a: '#FFEEDF', b: '#FFDCBF', ink: '#C2561A' },
    documents: { a: '#E9EEFF', b: '#D6E0FF', ink: '#2B4FB0' },
    warmth: { a: '#E2F7F3', b: '#C6EEE6', ink: '#14806F' },
    hygiene: { a: '#EEF1F4', b: '#DDE3E9', ink: '#4B5560' },
    pets: { a: '#F5ECE2', b: '#EBD9C4', ink: '#8A5A2B' },
  };
  const ITEM_CAT = { water: 'waterFood', food: 'waterFood', medicines: 'health', firstaid: 'health', flashlight: 'lightPower', batteries: 'lightPower', powerbank: 'lightPower', candles: 'lightPower', radio: 'information', cash: 'documents', documents: 'documents', blankets: 'warmth', hygiene: 'hygiene', petfood: 'pets', other: 'hygiene' };

  function svg(key, size = 120) {
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120" width="${size}" height="${size}">${ART[key] || ART.other}</svg>`;
  }
  root.NP_ART = { ART, CAT, ITEM_CAT, svg };
})(typeof window !== 'undefined' ? window : globalThis);
