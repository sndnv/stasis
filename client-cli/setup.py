from setuptools import setup, find_packages

install_requires = [
    'requests==2.31.0',
    'requests-oauthlib==1.3.1',
    'terminaltables==3.1.10',
    'hurry.filesize==0.9',
    'pyhocon==0.3.59',
    'click==8.1.3',
    'pyparsing==2.4.7',
    'psutil==5.9.4',
    'cryptography==41.0.2',
    'sseclient-py==1.7.2',
    'tqdm==4.64.1',
]

tests_require = []

setup(
    name='stasis-client-cli',
    license='Apache-2.0',
    url='https://github.com/sndnv/stasis',
    version='0.0.1',
    install_requires=install_requires,
    tests_require=tests_require,
    packages=find_packages(),
    entry_points={
        'console_scripts': [
            'stasis-client-cli = client_cli.__main__:main'
        ]
    }
)
