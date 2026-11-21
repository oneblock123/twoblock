const http = require('http');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const PORT = process.env.PORT || 3000;
const DATA_FILE = path.join(__dirname, 'data', 'applications.json');

// Ensure data file exists
if (!fs.existsSync(DATA_FILE)) {
  fs.writeFileSync(DATA_FILE, '[]', 'utf8');
}

function loadApplications() {
  try {
    const raw = fs.readFileSync(DATA_FILE, 'utf8');
    return JSON.parse(raw || '[]');
  } catch (err) {
    console.error('Error reading applications file:', err);
    return [];
  }
}

function saveApplications(apps) {
  try {
    fs.writeFileSync(DATA_FILE, JSON.stringify(apps, null, 2), 'utf8');
    return true;
  } catch (err) {
    console.error('Error saving applications file:', err);
    return false;
  }
}

const MIME_TYPES = {
  '.html': 'text/html; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.js': 'application/javascript; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon'
};

function parseRequestBody(req) {
  return new Promise((resolve, reject) => {
    let body = '';
    req.on('data', chunk => {
      body += chunk;
      if (body.length > 1e6) {
        req.destroy();
        reject(new Error('Payload too large'));
      }
    });
    req.on('end', () => {
      if (!body) return resolve({});
      try {
        resolve(JSON.parse(body));
      } catch (err) {
        reject(err);
      }
    });
    req.on('error', reject);
  });
}

function sendJSON(res, statusCode, data) {
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
    'Access-Control-Allow-Headers': 'Content-Type'
  });
  res.end(JSON.stringify(data));
}

function serveStatic(res, filePath) {
  fs.readFile(filePath, (err, content) => {
    if (err) {
      if (err.code === 'ENOENT') {
        res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('404 Not Found');
      } else {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        res.end('500 Internal Server Error');
      }
      return;
    }
    const ext = path.extname(filePath).toLowerCase();
    const contentType = MIME_TYPES[ext] || 'application/octet-stream';
    res.writeHead(200, { 'Content-Type': contentType });
    res.end(content);
  });
}

const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type'
    });
    res.end();
    return;
  }

  const urlObj = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = urlObj.pathname;

  // Static routes
  if (pathname === '/' || pathname === '/index.html') {
    return serveStatic(res, path.join(__dirname, 'views', 'index.html'));
  }

  if (pathname === '/admin' || pathname === '/admin.html') {
    return serveStatic(res, path.join(__dirname, 'views', 'admin.html'));
  }

  if (pathname.startsWith('/public/')) {
    const safePath = path.normalize(pathname.replace(/^\/public\//, ''));
    if (safePath.startsWith('..')) {
      res.writeHead(403);
      return res.end('Forbidden');
    }
    return serveStatic(res, path.join(__dirname, 'public', safePath));
  }

  // Check application status by ID (used by index.html to check status)
  if (pathname === '/api/applications/status' && req.method === 'GET') {
    const id = urlObj.searchParams.get('id');
    if (!id) {
      return sendJSON(res, 400, { success: false, error: 'ID not provided' });
    }
    const apps = loadApplications();
    const found = apps.find(a => a.id === id);
    if (!found) {
      return sendJSON(res, 404, { success: false, error: 'Заявка не найдена' });
    }
    return sendJSON(res, 200, {
      success: true,
      id: found.id,
      status: found.status,
      nickname: found.nickname
    });
  }

  // API Routes: List & Create
  if (pathname === '/api/applications') {
    if (req.method === 'GET') {
      const apps = loadApplications();
      return sendJSON(res, 200, { success: true, applications: apps });
    }

    if (req.method === 'POST') {
      try {
        const body = await parseRequestBody(req);
        
        if (!body.nickname || !body.nickname.trim()) {
          return sendJSON(res, 400, { success: false, error: 'Пожалуйста, укажите никнейм в Minecraft' });
        }
        if (!body.ageAndName || !body.ageAndName.trim()) {
          return sendJSON(res, 400, { success: false, error: 'Пожалуйста, укажите возраст и как к вам обращаться' });
        }

        const newApp = {
          id: 'tb-' + crypto.randomBytes(3).toString('hex'),
          createdAt: new Date().toISOString(),
          status: 'pending', // 'pending' | 'approved' | 'rejected'
          nickname: String(body.nickname || '').trim(),
          ageAndName: String(body.ageAndName || '').trim(),
          yearsPlaying: String(body.yearsPlaying || '').trim(),
          gameStyles: Array.isArray(body.gameStyles) ? body.gameStyles : (body.gameStyles ? [body.gameStyles] : []),
          skillLevel: String(body.skillLevel || 'Средний').trim(),
          plansOnServer: String(body.plansOnServer || '').trim(),
          playStyleAndFriends: String(body.playStyleAndFriends || '').trim(),
          timeCommitment: String(body.timeCommitment || '').trim(),
          whyVanilla: String(body.whyVanilla || '').trim(),
          communityImportance: String(body.communityImportance || '').trim(),
          rulesAgreed: body.rulesAgreed === 'Да' || body.rulesAgreed === true ? 'Да' : 'Нет',
          microphone: String(body.microphone || '').trim()
        };

        const apps = loadApplications();
        apps.unshift(newApp);
        saveApplications(apps);

        console.log(`[Новая заявка] ${newApp.nickname} (${newApp.id})`);

        return sendJSON(res, 201, {
          success: true,
          message: 'Заявка успешно отправлена',
          applicationId: newApp.id,
          application: newApp
        });
      } catch (err) {
        console.error('Error handling POST /api/applications:', err);
        return sendJSON(res, 400, { success: false, error: 'Неверные данные формы' });
      }
    }
  }

  // Update application (status: approved / rejected)
  if (pathname.startsWith('/api/applications/')) {
    const id = pathname.replace('/api/applications/', '');

    if (req.method === 'PATCH') {
      try {
        const body = await parseRequestBody(req);
        const apps = loadApplications();
        const index = apps.findIndex(a => a.id === id);

        if (index === -1) {
          return sendJSON(res, 404, { success: false, error: 'Заявка не найдена' });
        }

        if (body.status && ['pending', 'approved', 'rejected'].includes(body.status)) {
          apps[index].status = body.status;
        }

        saveApplications(apps);
        console.log(`[Статус обновлен] ${apps[index].nickname} -> ${apps[index].status}`);
        return sendJSON(res, 200, { success: true, application: apps[index] });
      } catch (err) {
        return sendJSON(res, 400, { success: false, error: err.message });
      }
    }

    if (req.method === 'DELETE') {
      const apps = loadApplications();
      const filtered = apps.filter(a => a.id !== id);
      if (filtered.length === apps.length) {
        return sendJSON(res, 404, { success: false, error: 'Заявка не найдена' });
      }
      saveApplications(filtered);
      return sendJSON(res, 200, { success: true, message: 'Заявка удалена' });
    }
  }

  res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' });
  res.end('404 Not Found');
});

server.listen(PORT, () => {
  console.log(`Сервер Два Блока запущен: http://localhost:${PORT}`);
});
