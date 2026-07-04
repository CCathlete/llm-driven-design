import os
from pathlib import Path
import sys


class FileProcessor:
    """Process files in the given directory."""

    def __init__(self, base_path):
        self.base_path = Path(base_path)

    def list_files(self):
        return [f for f in self.base_path.iterdir() if f.is_file()]

    def process(self):
        files = self.list_files()
        return {f.name: f.stat().st_size for f in files}


def create_processor(path):
    return FileProcessor(path)
