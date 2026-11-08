// Prueba de la ruta web/escritorio: carga el `.js` compilado por Kotlin/JS y
// ejercita la misma lógica que la app. No hay simulacro: es el artefacto real.
//
// Se ejecuta con:  node scripts/probar-web.mjs
// Sale con código 1 si algo falla, para que sirva en CI.
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const aquí = dirname(fileURLToPath(import.meta.url));
const require = createRequire(import.meta.url);
const ruta = join(aquí, '..', 'build', 'dist', 'js', 'productionLibrary', 'cerox-comun.js');

const mod = require(ruta);
const api = mod.com?.cerox?.comun?.ApiWeb;
if (!api) {
  console.error('FALLO: el artefacto no exporta com.cerox.comun.ApiWeb');
  process.exit(1);
}

const pruebas = [];
function prueba(nombre, fn) {
  try { fn(); pruebas.push([nombre, true]); }
  catch (e) { pruebas.push([nombre, false, e.message]); }
}
function igual(a, b) {
  const sa = JSON.stringify(a), sb = JSON.stringify(b);
  if (sa !== sb) throw new Error(`esperado ${sb}, recibido ${sa}`);
}

prueba('expone version y plataforma', () => {
  igual(api.version, '1.0.0');
  igual(api.plataforma(), 'js');
});

prueba('el 2 va antes que el 10 (la misma regla que en Android)', () => {
  igual(api.ordenar(['10.jpg', '2.jpg', '1.jpg', '20.jpg']), ['1.jpg', '2.jpg', '10.jpg', '20.jpg']);
});

prueba('orden natural con ceros delante', () => {
  igual(api.ordenar(['003.jpg', '01.jpg', '002.jpg', '0001.jpg']),
    ['0001.jpg', '01.jpg', '002.jpg', '003.jpg']);
});

prueba('numeroDe extrae el número del capítulo', () => {
  igual(api.numeroDe('Capítulo 12'), 12);
  igual(api.numeroDe('Cap 3,5'), 3.5);
  igual(api.numeroDe('Extras'), -1);
});

prueba('la cola nunca pasa de dos descargas y ahorro va de una', () => {
  igual(api.concurrencia('ahorro'), 1);
  igual(api.concurrencia('rendimiento'), 2);
});

prueba('la espera crece y tiene techo de 30 s', () => {
  igual(api.esperaAntesDelIntento(1), 1000);
  igual(api.esperaAntesDelIntento(2), 2000);
  igual(api.esperaAntesDelIntento(3), 4000);
  if (api.esperaAntesDelIntento(20) > 30000) throw new Error('se pasa del techo');
});

prueba('un .exe no hereda el tope de un vídeo', () => {
  if (api.topePorTipo('a.exe') >= api.topePorTipo('a.mp4')) throw new Error('el .exe hereda el tope del vídeo');
  igual(api.topePorTipo('tomo.epub'), api.topePorTipo('libro.pdf'));
});

prueba('sin tamaño no hay progreso', () => {
  igual(api.progreso(500, 0), 0);
  igual(api.progreso(50, 100), 0.5);
});

prueba('no se retrocede el progreso de un tracker', () => {
  const r = api.planDeTracker(12, 40, 200);
  if (!r.startsWith('OMITIR_POR_RETROCESO|')) throw new Error(r);
});

prueba('el tracker envía cuando el local va por delante', () => {
  const r = api.planDeTracker(41, 40, 200);
  if (!r.startsWith('ENVIAR|')) throw new Error(r);
});

prueba('la variante dominicana cambia el vocabulario', () => {
  igual(api.variante('Voy en autobús con una banana', 'es-DO', [], []),
    'Voy en guagua con una guineo');
});

prueba('el glosario del usuario gana a la variante', () => {
  const r = api.variante('El móvil', 'es-DO', ['móvil'], ['teléfono']);
  igual(r, 'El teléfono');
});

prueba('el sobre envuelve la extensión', () => {
  const s = api.sobreDeExtension('var a = 1;');
  if (!s.startsWith('(function (ceroX, http, dom, prefs, log)')) throw new Error(s.slice(0, 40));
  if (!s.includes('"use strict"')) throw new Error('sin modo estricto');
});

prueba('lee los nombres declarados de un JSON con acentos', () => {
  igual(api.nombresDeclarados('[{"name":"Año","lang":"es"}]'), ['Año']);
});

prueba('la tabla de capacidades dice que en web no hay lector', () => {
  const filas = api.capacidades();
  const lector = filas.find((f) => f.startsWith('lector|'));
  if (!lector.includes('|false')) throw new Error('la web promete lector: ' + lector);
  const js = filas.find((f) => f.startsWith('extensiones-js|'));
  if (!js.endsWith('true')) throw new Error('la web debería poder con extensiones: ' + js);
});

// ── Informe ────────────────────────────────────────────────────────────────
let fallos = 0;
for (const [nombre, ok, error] of pruebas) {
  console.log(`${ok ? 'ok  ' : 'FALLO'} ${nombre}${ok ? '' : ' -> ' + error}`);
  if (!ok) fallos++;
}
console.log(`\n${pruebas.length - fallos}/${pruebas.length} en verde`);
process.exit(fallos === 0 ? 0 : 1);
