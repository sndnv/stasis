from setuptools import setup, find_packages

install_requires = [
    'requests==2.22.0',
    'requests-oauthlib==1.3.0',
    'terminaltables==3.1.0',
    'hurry.filesize==0.9',
    'pyhocon==0.3.51',
    'click==7.0',
    'pyparsing==2.4.5',
    'psutil==5.6.7',
    'cryptography==2.8',
]

tests_require = [
    'pyjwt==1.7.1',
]

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
