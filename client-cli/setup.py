from setuptools import setup, find_packages

install_requires = [
    'requests==2.31.0',
    'requests-oauthlib==2.0.0',
    'terminaltables==3.1.10',
    'hurry.filesize==0.9',
    'pyhocon==0.3.60',
    'click==8.1.7',
    'pyparsing==3.1.2',
    'psutil==5.9.8',
    'cryptography==42.0.7',
    'sseclient-py==1.8.0',
    'tqdm==4.66.4',
]

tests_require = []

setup(
    name='stasis-client-cli',
    license='Apache-2.0',
    url='https://github.com/sndnv/stasis',
    version='1.0.1+SNAPSHOT',
    install_requires=install_requires,
    tests_require=tests_require,
    packages=find_packages(),
    entry_points={
        'console_scripts': [
            'stasis-client-cli = client_cli.__main__:main'
        ]
    }
)
