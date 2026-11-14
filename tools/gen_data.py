#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 App 内置的公开中文词典：只覆盖任何一台 Android 手机都可能出现的目录。

刻意不收录开发者本机的个人目录（工作区、项目、密钥库、下载的临时文件等），
也不依赖任何本机导出的目录树——发布版数据完全通用、可重建。
"""
import json, os

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'assets', 'builtin.json')

# (路径, 中文名, 等级) —— S 系统 / A 珍贵 / B 项目 / C 普通 / D 可清 / E 空置
STD = [
    # 系统
    ('Android', '系统目录：应用数据 / 媒体 / obb', 'S'),
    ('Android/data', '应用私有数据（受系统限制）', 'S'),
    ('Android/media', '应用写入的媒体文件', 'S'),
    ('Android/obb', '大型游戏的 obb 数据包', 'S'),
    ('Android/com', '按包名存放的应用数据', 'S'),
    ('ColorOS', 'ColorOS 系统目录', 'S'),
    ('ColorOS/Kernel', '内核相关', 'S'),
    ('ColorOS/ThemeStore', '主题商店', 'S'),
    ('ColorOS/.UserCenter', '用户中心', 'S'),
    ('Huawei', '华为系统目录', 'S'),
    ('MIUI', 'MIUI 系统目录', 'S'),
    ('OPPO', 'OPPO 系统目录', 'S'),
    ('Samsung', '三星系统目录', 'S'),
    ('Xiaomi', '小米系统目录', 'S'),
    ('vivo', 'vivo 系统目录', 'S'),
    ('realme', 'realme 系统目录', 'S'),
    ('OnePlus', '一加系统目录', 'S'),

    # 相机与图片
    ('DCIM', '相机照片与视频', 'A'),
    ('DCIM/Camera', '相机拍摄的照片与视频', 'A'),
    ('DCIM/Screenshots', '截屏', 'A'),
    ('DCIM/.thumbnails', '相册缩略图缓存', 'D'),
    ('Pictures', '图片', 'A'),
    ('Pictures/Screenshots', '截屏', 'A'),
    ('Pictures/WeiXin', '微信保存的图片', 'C'),
    ('Pictures/QQ', 'QQ 保存的图片', 'C'),
    ('Pictures/douyin', '抖音保存的图片', 'C'),
    ('Pictures/com.baidu.searchbox', '百度 App 保存的图片', 'C'),
    ('Pictures/QuarkPictures', '夸克浏览器保存的图片', 'C'),
    ('Pictures/.thumbnails', '缩略图缓存', 'D'),

    # 媒体
    ('Music', '音乐', 'A'),
    ('Music/.thumbnails', '专辑缩略图缓存', 'D'),
    ('Movies', '视频', 'A'),
    ('Movies/.thumbnails', '视频缩略图缓存', 'D'),
    ('Recordings', '录音', 'A'),
    ('Alarms', '系统闹钟铃声', 'C'),
    ('Notifications', '通知音', 'E'),
    ('Ringtones', '铃声', 'E'),
    ('Podcasts', '播客', 'E'),
    ('Audiobooks', '有声书', 'E'),
    ('MoBiMusic', '音乐缓存与下载', 'A'),

    # 下载与文档
    ('Download', '浏览器 / 各 App 的下载文件', 'C'),
    ('Download/netease', '网易云音乐下载', 'C'),
    ('Download/QuarkDownloads', '夸克浏览器下载', 'C'),
    ('Download/UCDownloads', 'UC 浏览器下载', 'C'),
    ('Download/BaiduNetdisk', '百度网盘下载', 'C'),
    ('Download/com.tencent.game', '腾讯游戏下载', 'C'),
    ('Download/update', '应用更新包', 'C'),
    ('Documents', '文档', 'A'),
    ('Documents/notes', '笔记', 'A'),
    ('Documents/Books', '电子书', 'A'),

    # 应用数据（通用大厂目录）
    ('Tencent', '微信 / QQ 数据', 'A'),
    ('Tencent/MicroMsg', '微信（MicroMsg）数据', 'A'),
    ('Tencent/MobileQQ', '手机 QQ 数据', 'A'),
    ('Tencent/tbs', '腾讯浏览服务（X5 内核）', 'C'),
    ('Tencent/Midas', '米大师支付 SDK', 'C'),
    ('Tencent/MidasPay', '米大师支付', 'C'),
    ('Tencent/msflogs', 'MSF 网络日志', 'D'),
    ('WhatsApp', 'WhatsApp 文件', 'A'),
    ('WhatsApp/Media', 'WhatsApp 接收的媒体', 'A'),
    ('Telegram', 'Telegram 文件', 'A'),
    ('BaiduNetdisk', '百度网盘', 'A'),
    ('netease', '网易云音乐', 'A'),
    ('netease/cloudmusic', '网易云音乐数据', 'A'),

    # 其他常见
    ('backups', '应用备份', 'C'),
    ('Browser', '浏览器数据', 'D'),
    ('Browser/.config', '浏览器配置', 'D'),
    ('ByteDownload', '字节系 App 的下载目录', 'E'),
    ('Fonts', '字体', 'E'),
    ('My Documents', '我的文档', 'E'),
    ('log', '日志目录', 'E'),
]

GLOSSARY = {
    'cache': '缓存', '.cache': '缓存', 'code_cache': '代码缓存', 'databases': '数据库',
    'shared_prefs': '应用偏好设置', 'files': '应用私有文件', 'no_backup': '不参与备份',
    'DCIM': '相机照片与视频', 'Pictures': '图片', 'Music': '音乐', 'Movies': '视频',
    'Documents': '文档', 'Download': '下载', 'Downloads': '下载', 'Screenshots': '截屏',
    'Camera': '相机', 'Android': '系统目录', 'data': '数据', 'obb': '游戏数据包',
    'media': '媒体文件', 'debug': '调试产物', 'build': '构建输出', 'out': '构建输出',
    'res': '资源', 'src': '源码', 'assets': '资源文件', 'lib': '库文件', 'libs': '依赖库',
    'java': 'Java 源码', 'kotlin': 'Kotlin 源码', 'test': '测试', 'tests': '测试',
    'values': '资源值', 'drawable': '图片资源', 'layout': '布局资源',
    'mipmap-anydpi-v26': '自适应图标', 'META-INF': '签名与清单',
    'node_modules': 'Node 依赖', 'gradle': 'Gradle 构建', '.gradle': 'Gradle 缓存',
    '.git': 'Git 仓库', '.github': 'GitHub 配置', 'scripts': '脚本', 'tools': '工具',
    'docs': '文档', 'logs': '日志', 'log': '日志', 'tmp': '临时文件', 'temp': '临时文件',
    'backup': '备份', 'Backups': '备份', 'cache2': '缓存',
    '.thumbnails': '缩略图缓存', 'thumbnails': '缩略图缓存',
    '.trashed': '回收站', '.recycle.bin': '回收站',
    'MicroMsg': '微信数据', 'MobileQQ': '手机 QQ', 'tbs': '腾讯 X5 内核',
    'Midas': '米大师支付', 'msflogs': 'MSF 日志', 'WeiXin': '微信图片', 'QQ': 'QQ 文件',
    'netease': '网易云音乐', 'cloudmusic': '网易云音乐', 'baidu': '百度',
    'BaiduNetdisk': '百度网盘', 'WhatsApp': 'WhatsApp 文件', 'Telegram': 'Telegram 文件',
    'MIUI': 'MIUI 系统目录', 'Huawei': '华为系统目录', 'OPPO': 'OPPO 系统目录',
    'vivo': 'vivo 系统目录', 'Xiaomi': '小米系统目录', 'Samsung': '三星系统目录',
    'QuarkCloudDrive': '夸克网盘', 'QuarkDownloads': '夸克下载', 'QuarkPictures': '夸克图片',
    'UCDownloads': 'UC 下载', 'ADM': 'ADM 下载器', '1DMP': '1DM+ 下载器',
    'Agora': '声网 SDK', 'keys': '密钥', 'export': '导出', 'book': '电子书',
    'dict': '词典', 'config': '配置', 'conf': '配置', 'protect': '保护',
    'apks': 'APK 文件', 'install': '安装包', 'plugins': '插件', 'restore': '恢复',
    'update': '更新包', 'arm64-v8a': '64 位 ARM 原生库', 'armeabi-v7a': '32 位 ARM 原生库',
    'x86': 'x86 原生库', 'fonts': '字体', 'shaders': '着色器', 'raw': '原始资源',
    'menu': '菜单资源', 'anim': '动画资源', 'animator': '属性动画',
    'navigation': '导航资源', 'xml': 'XML 资源', 'color': '颜色资源',
    'values-night': '深色资源', 'values-zh-rCN': '简体中文资源',
    'mipmap-hdpi': '图标资源', 'drawable-xxhdpi': '高清图片资源',
    'layout-land': '横屏布局', 'flutter_assets': 'Flutter 资源',
    'screens': '页面', 'model': '数据模型', 'feature': '功能模块', 'helpers': '辅助代码',
    'commands': '命令', 'example': '示例', 'json': 'JSON 数据', 'script': '脚本',
    'temp_dir': '临时目录', 'trash': '回收站', 'Videos': '视频', 'Photos': '照片',
    'Subtitles': '字幕', 'Torrents': '种子', 'Programs': '程序 / 安装包',
    'Compressed': '压缩包', 'Others': '其他', 'General': '通用',
    'recordings': '录音', 'Recordings': '录音', 'ringtones': '铃声', 'Ringtones': '铃声',
    'alarms': '闹钟', 'notifications': '通知音', 'podcasts': '播客', 'audiobooks': '有声书',
}

paths = {p: n for p, n, _ in STD}
levels = {p: lv for p, _, lv in STD}

data = {
    'generatedFrom': 'public-android-storage',
    'paths': paths,
    'levels': levels,
    'glossary': GLOSSARY,
}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, separators=(',', ':'))

print('路径词典', len(paths), '等级', len(levels), '通用词', len(GLOSSARY))
print('体积', round(os.path.getsize(OUT) / 1024, 1), 'KB')
