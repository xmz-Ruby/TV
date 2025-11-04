# -*- coding: utf-8 -*-
"""
测试爬虫示例
这是一个简单的Python爬虫示例，用于测试pyramid模块
"""

from base.spider import Spider


class Spider(Spider):

    def getName(self):
        return "测试爬虫"

    def init(self, extend=""):
        self.extend = extend
        print("测试爬虫初始化成功")

    def homeContent(self, filter):
        result = {
            "class": [
                {"type_id": "1", "type_name": "电影"},
                {"type_id": "2", "type_name": "电视剧"},
                {"type_id": "3", "type_name": "综艺"},
                {"type_id": "4", "type_name": "动漫"}
            ],
            "filters": {}
        }
        return result

    def categoryContent(self, tid, pg, filter, extend):
        result = {
            "page": int(pg),
            "pagecount": 10,
            "limit": 20,
            "total": 200,
            "list": [
                {
                    "vod_id": "test_1",
                    "vod_name": "测试视频1",
                    "vod_pic": "https://example.com/pic1.jpg",
                    "vod_remarks": "测试"
                }
            ]
        }
        return result

    def detailContent(self, ids):
        result = {
            "list": [
                {
                    "vod_id": ids[0],
                    "vod_name": "测试视频详情",
                    "vod_pic": "https://example.com/pic.jpg",
                    "vod_content": "这是一个测试视频",
                    "vod_play_from": "测试源",
                    "vod_play_url": "第1集$https://example.com/video1.mp4"
                }
            ]
        }
        return result

    def searchContent(self, key, quick, pg="1"):
        result = {
            "page": int(pg),
            "pagecount": 1,
            "limit": 20,
            "total": 1,
            "list": [
                {
                    "vod_id": "search_1",
                    "vod_name": f"搜索结果: {key}",
                    "vod_pic": "https://example.com/pic.jpg",
                    "vod_remarks": "测试"
                }
            ]
        }
        return result

    def playerContent(self, flag, id, vipFlags):
        result = {
            "parse": 0,
            "playUrl": "",
            "url": id
        }
        return result
