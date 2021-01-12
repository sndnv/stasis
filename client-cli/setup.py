from setuptools import setup, find_packages

install_requires = [
    'requests==2.25.1',
    'requests-oauthlib==1.3.0',
    'terminaltables==3.1.0',
    'hurry.filesize==0.9',
    'pyhocon==0.3.57',
    'click==7.1.2',
    'pyparsing==2.4.7',
    'psutil==5.8.0',
    'cryptography==3.3.1',
    'sseclient-py==1.7',
    'tqdm==4.56.0',
]

tests_require = []

setup(
    name='stasis-client-cli',
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
