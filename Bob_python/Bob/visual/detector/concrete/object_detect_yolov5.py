from typing import List

import cv2
from Bob.visual.detector.framework.detector import Detector
import torch
from PIL import Image


class ObjectDetector(Detector):

    def __init__(self, _id, conf: float = 0.25):
        super().__init__(_id)
        model = torch.hub.load('ultralytics/yolov5', 'yolov5s', pretrained=True, _verbose=False)
        model.conf = conf  # NMS confidence threshold
        model.iou = 0.45  # NMS IoU threshold
        self._module = model

    def detect(self, image):
        img = Image.fromarray(cv2.cvtColor(image.copy(), cv2.COLOR_BGR2RGB))

        detections = self._module(img)

        r = detections.pandas().xyxy[0]
        results: List = []
        for i in range(0, len(r.name.values)):
            name = r.name.values[i]
            conf = r.confidence.values[i]
            xmax = int(r.xmax.values[i])
            ymax = int(r.ymax.values[i])

            xmin = int(r.xmin.values[i])
            ymin = int(r.ymin.values[i])
            results.append(
                {'name': name, 'conf': conf, 'x': {'min': xmin, 'max': xmax}, 'y': {'min': ymin, 'max': ymax}})

        return results