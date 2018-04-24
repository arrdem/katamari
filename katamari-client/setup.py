'''
    nrepl-python-client
    -------------------

    A Python client for the nREPL Clojure networked-REPL server.

    Links
    `````
    * `development version <https://github.com/cemerick/nrepl-python-client>`_
'''

import os

from setuptools import setup, find_packages

# HACK: Pull the version number without requiring the package to be installed
# beforehand, i.e. without using import.
module_path = os.path.join(os.path.dirname(__file__), 'nrepl/__init__.py')
version_line = [line for line in open(module_path)
                if line.startswith('__version_info__')][0]

__version__ = '.'.join(eval(version_line.split('__version_info__ = ')[-1]))

description = "A Python client for the nREPL Clojure networked-REPL server."
classifiers = ['Development Status :: 4 - Beta',
               'Environment :: Console',
               'Intended Audience :: Developers',
               'License :: OSI Approved :: MIT License',
               'Natural Language :: English',
               'Operating System :: OS Independent',
               'Programming Language :: Python',
               'Topic :: Software Development :: Interpreters',
               'Topic :: Software Development :: Libraries :: Python Modules',
               'Topic :: Utilities']

setup(name="nrepl-python-client",
      version=__version__,
      packages=find_packages(),
      # metadata for upload to PyPI
      author="Chas Emerick",
      author_email="chas@cemerick.com",
      description=description,
      long_description=__doc__,
      test_suite='test',
      license="MIT License",
      keywords="clojure repl nrepl",
      url="https://github.com/cemerick/nrepl-python-client",
      zip_safe=True,
      platforms='any',
      classifiers=classifiers)
