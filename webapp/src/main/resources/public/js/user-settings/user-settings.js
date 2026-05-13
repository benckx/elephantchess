/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

const UI_NOTIFICATION_TIMEOUT = 2_500;

const USER_SETTINGS_API = '/api/user/settings';
const PROFILE_URL = USER_SETTINGS_API + '/profile';
const PROFILE_PICTURE_UPLOAD_URL = USER_SETTINGS_API + '/profile-picture';
const EMAIL_SETTINGS_URL = USER_SETTINGS_API + '/email-address';

const USERNAME_MAX_DESCRIPTION_LENGTH = 1_000;
const PROFILE_PICTURE_MAX_BYTES = 500 * 1024;
const PROFILE_PICTURE_SIZE_PX = 100;
// Keep JPEG uploads visually crisp while staying comfortably under the 500KB limit.
const PROFILE_PICTURE_JPEG_QUALITY = 0.92;
const DEFAULT_PROFILE_PICTURE_URL = '/images/icons/user_profile_smaller.png';

class UserSettingsPage extends BasePage {

    // profile section
    #saveProfileButton = document.getElementById('save-profile-button');
    #descriptionField = document.getElementById('description');
    #countryField = document.getElementById('countries');
    #descriptionCharacterCounter = document.getElementById('description-character-counter');
    #profilePicturePreview = document.getElementById('profile-picture-preview');
    #profilePictureInput = document.getElementById('profile-picture-input');
    #selectProfilePictureButton = document.getElementById('select-profile-picture-button');
    #profilePictureEditor = document.getElementById('profile-picture-editor');
    #profilePictureEditorCanvas = document.getElementById('profile-picture-editor-canvas');
    #profilePictureZoomField = document.getElementById('profile-picture-zoom');
    #uploadProfilePictureButton = document.getElementById('upload-profile-picture-button');

    // email notifications section
    #notificationSettingsWidget = new NotificationSettingsWidget();
    #saveNotificationsButton = document.getElementById('save-notifications-button');

    // TODO: email address section
    #emailAddressField = document.getElementById('email-address');

    #profilePictureImage = null;
    #profilePictureCrop = null;
    #profilePictureObjectUrl = null;
    #profilePictureMimeType = 'image/png';
    #profilePictureExtension = 'png';
    #isDraggingProfilePicture = false;
    #profilePictureDragOffsetX = 0;
    #profilePictureDragOffsetY = 0;

    constructor() {
        super();

        // profile section
        fillSelect('countries');
        this.#fetchProfileSettings();
        this.#saveProfileButton.addEventListener('click', () => this.#updateProfileSettings());
        this.#descriptionField.addEventListener('input', () => this.#updateDescriptionCharacterCounter());
        this.#descriptionField.setAttribute('maxlength', USERNAME_MAX_DESCRIPTION_LENGTH.toString());
        this.#selectProfilePictureButton.addEventListener('click', () => this.#profilePictureInput.click());
        this.#profilePictureInput.addEventListener('change', () => this.#loadProfilePictureSelection());
        this.#profilePictureZoomField.addEventListener('input', () => this.#updateProfilePictureCropFromZoom());
        this.#uploadProfilePictureButton.addEventListener('click', () => this.#uploadProfilePicture());
        this.#profilePictureEditorCanvas.addEventListener('pointerdown', event => this.#startDraggingProfilePicture(event));
        this.#profilePictureEditorCanvas.addEventListener('pointermove', event => this.#dragProfilePicture(event));
        this.#profilePictureEditorCanvas.addEventListener('pointerup', event => this.#stopDraggingProfilePicture(event));
        this.#profilePictureEditorCanvas.addEventListener('pointerleave', event => this.#stopDraggingProfilePicture(event));

        // notifications section
        let notificationsSettingsTable = document.getElementById('notifications-settings-table');
        this.#notificationSettingsWidget.renderToTable(notificationsSettingsTable);
        this.#saveNotificationsButton.addEventListener('click', () => {
            this.#notificationSettingsWidget.updateSettings(() => {
                UI.pushInfoNotification('Notifications settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
            })
        });

        // email address section
        this.#fetchEmailAddressSettings();
    }

    #fetchProfileSettings() {
        getAndHandle(PROFILE_URL, json => {
            this.#descriptionField.value = json.description;
            this.#updateDescriptionCharacterCounter();
            this.#setProfilePicturePreview(json.profilePictureUrl);
            if (json.country != null) {
                let countryName = getCountryName(json.country)
                if (countryName != null) {
                    let select = document.getElementById('countries');
                    let options = select.getElementsByTagName('option');
                    for (let i = 0; i < options.length; i++) {
                        let option = options[i];
                        if (option.value.toLowerCase() === json.country.toLowerCase()) {
                            option.selected = true;
                            break;
                        }
                    }
                }
            }
        });
    }

    // TODO: 'none' option selected
    #updateProfileSettings() {
        let description = this.#descriptionField.value;
        let country = this.#countryField.value;
        let body = {'description': description, 'country': country};
        postAndHandle(PROFILE_URL, body, () => {
            UI.pushInfoNotification('Profile settings successfully updated!', UI_NOTIFICATION_TIMEOUT);
        });
    }

    #updateDescriptionCharacterCounter() {
        this.#descriptionCharacterCounter.innerText = `${this.#descriptionField.value.length} / ${USERNAME_MAX_DESCRIPTION_LENGTH}`;
    }

    #setProfilePicturePreview(url) {
        this.#profilePicturePreview.src = url ?? DEFAULT_PROFILE_PICTURE_URL;
    }

    #loadProfilePictureSelection() {
        const file = this.#profilePictureInput.files?.[0];
        if (file == null) {
            return;
        }

        const fileFormat = this.#resolveProfilePictureFormat(file.name);
        if (fileFormat == null) {
            UI.pushErrorNotification('Only PNG and JPEG profile pictures are supported', 3_000);
            return;
        }

        if (this.#profilePictureObjectUrl != null) {
            URL.revokeObjectURL(this.#profilePictureObjectUrl);
        }

        this.#profilePictureExtension = fileFormat.extension;
        this.#profilePictureMimeType = fileFormat.mimeType;
        this.#profilePictureObjectUrl = URL.createObjectURL(file);

        const image = new Image();
        image.onload = () => {
            const size = Math.min(image.naturalWidth, image.naturalHeight);
            this.#profilePictureImage = image;
            this.#profilePictureCrop = {
                x: (image.naturalWidth - size) / 2,
                y: (image.naturalHeight - size) / 2,
                size: size,
            };
            this.#profilePictureZoomField.value = '100';
            this.#profilePictureEditor.classList.remove('hidden');
            this.#renderProfilePictureEditor();
        };
        image.src = this.#profilePictureObjectUrl;
    }

    #resolveProfilePictureFormat(fileName) {
        const fileExtension = fileName.split('.').pop()?.toLowerCase();
        if (fileExtension === 'png') {
            return {extension: 'png', mimeType: 'image/png'};
        }
        if (fileExtension === 'jpg' || fileExtension === 'jpeg') {
            return {extension: fileExtension, mimeType: 'image/jpeg'};
        }
        return null;
    }

    #renderProfilePictureEditor() {
        if (this.#profilePictureImage == null || this.#profilePictureCrop == null) {
            return;
        }

        const canvas = this.#profilePictureEditorCanvas;
        const context = canvas.getContext('2d');
        const image = this.#profilePictureImage;
        const crop = this.#profilePictureCrop;
        const scale = Math.min(canvas.width / image.naturalWidth, canvas.height / image.naturalHeight);
        const drawWidth = image.naturalWidth * scale;
        const drawHeight = image.naturalHeight * scale;
        const offsetX = (canvas.width - drawWidth) / 2;
        const offsetY = (canvas.height - drawHeight) / 2;

        context.clearRect(0, 0, canvas.width, canvas.height);
        context.drawImage(image, offsetX, offsetY, drawWidth, drawHeight);

        const cropX = offsetX + crop.x * scale;
        const cropY = offsetY + crop.y * scale;
        const cropSize = crop.size * scale;

        context.fillStyle = 'rgba(0, 0, 0, 0.45)';
        context.fillRect(0, 0, canvas.width, canvas.height);
        context.clearRect(cropX, cropY, cropSize, cropSize);
        context.strokeStyle = '#f7f0e7';
        context.lineWidth = 2;
        context.strokeRect(cropX, cropY, cropSize, cropSize);
    }

    #updateProfilePictureCropFromZoom() {
        if (this.#profilePictureImage == null || this.#profilePictureCrop == null) {
            return;
        }

        const image = this.#profilePictureImage;
        const shortestSide = Math.min(image.naturalWidth, image.naturalHeight);
        const nextSize = shortestSide * (Number(this.#profilePictureZoomField.value) / 100);
        const centerX = this.#profilePictureCrop.x + this.#profilePictureCrop.size / 2;
        const centerY = this.#profilePictureCrop.y + this.#profilePictureCrop.size / 2;

        this.#profilePictureCrop.size = nextSize;
        this.#profilePictureCrop.x = Math.max(0, Math.min(image.naturalWidth - nextSize, centerX - nextSize / 2));
        this.#profilePictureCrop.y = Math.max(0, Math.min(image.naturalHeight - nextSize, centerY - nextSize / 2));
        this.#renderProfilePictureEditor();
    }

    #eventToProfilePictureCoordinates(event) {
        if (this.#profilePictureImage == null) {
            return null;
        }

        const canvas = this.#profilePictureEditorCanvas;
        const image = this.#profilePictureImage;
        const bounds = canvas.getBoundingClientRect();
        const scale = Math.min(canvas.width / image.naturalWidth, canvas.height / image.naturalHeight);
        const drawWidth = image.naturalWidth * scale;
        const drawHeight = image.naturalHeight * scale;
        const offsetX = (canvas.width - drawWidth) / 2;
        const offsetY = (canvas.height - drawHeight) / 2;
        const canvasX = (event.clientX - bounds.left) * (canvas.width / bounds.width);
        const canvasY = (event.clientY - bounds.top) * (canvas.height / bounds.height);

        if (canvasX < offsetX || canvasX > offsetX + drawWidth || canvasY < offsetY || canvasY > offsetY + drawHeight) {
            return null;
        }

        return {
            x: (canvasX - offsetX) / scale,
            y: (canvasY - offsetY) / scale,
        };
    }

    #startDraggingProfilePicture(event) {
        if (this.#profilePictureCrop == null) {
            return;
        }

        const coordinates = this.#eventToProfilePictureCoordinates(event);
        if (coordinates == null) {
            return;
        }

        const crop = this.#profilePictureCrop;
        const isInsideCrop =
            coordinates.x >= crop.x &&
            coordinates.x <= crop.x + crop.size &&
            coordinates.y >= crop.y &&
            coordinates.y <= crop.y + crop.size;

        if (isInsideCrop) {
            this.#isDraggingProfilePicture = true;
            this.#profilePictureDragOffsetX = coordinates.x - crop.x;
            this.#profilePictureDragOffsetY = coordinates.y - crop.y;
            this.#profilePictureEditorCanvas.setPointerCapture(event.pointerId);
        }
    }

    #dragProfilePicture(event) {
        if (!this.#isDraggingProfilePicture || this.#profilePictureCrop == null || this.#profilePictureImage == null) {
            return;
        }

        const coordinates = this.#eventToProfilePictureCoordinates(event);
        if (coordinates == null) {
            return;
        }

        const crop = this.#profilePictureCrop;
        crop.x = Math.max(0, Math.min(this.#profilePictureImage.naturalWidth - crop.size, coordinates.x - this.#profilePictureDragOffsetX));
        crop.y = Math.max(0, Math.min(this.#profilePictureImage.naturalHeight - crop.size, coordinates.y - this.#profilePictureDragOffsetY));
        this.#renderProfilePictureEditor();
    }

    #stopDraggingProfilePicture(event) {
        if (this.#isDraggingProfilePicture) {
            this.#isDraggingProfilePicture = false;
            if (event.pointerId != null && this.#profilePictureEditorCanvas.hasPointerCapture(event.pointerId)) {
                this.#profilePictureEditorCanvas.releasePointerCapture(event.pointerId);
            }
        }
    }

    #uploadProfilePicture() {
        if (this.#profilePictureImage == null || this.#profilePictureCrop == null) {
            UI.pushErrorNotification('Please choose a profile picture first', 3_000);
            return;
        }

        const cropCanvas = document.createElement('canvas');
        cropCanvas.width = PROFILE_PICTURE_SIZE_PX;
        cropCanvas.height = PROFILE_PICTURE_SIZE_PX;
        cropCanvas.getContext('2d').drawImage(
            this.#profilePictureImage,
            this.#profilePictureCrop.x,
            this.#profilePictureCrop.y,
            this.#profilePictureCrop.size,
            this.#profilePictureCrop.size,
            0,
            0,
            PROFILE_PICTURE_SIZE_PX,
            PROFILE_PICTURE_SIZE_PX
        );

        cropCanvas.toBlob(blob => {
            if (blob == null) {
                UI.pushErrorNotification('Unable to prepare the profile picture', 3_000);
                return;
            }

            if (blob.size > PROFILE_PICTURE_MAX_BYTES) {
                UI.pushErrorNotification('Profile picture limited to 500KB', 3_000);
                return;
            }

            const formData = new FormData();
            formData.append('file', blob, `profile-picture.${this.#profilePictureExtension}`);

            const requestHeaders = {'Accept': 'application/json'};
            const token = getToken();
            if (token != null) {
                requestHeaders.Authorization = `Bearer ${token}`;
            }

            const handler = new SimpleResponseHandler(json => {
                this.#setProfilePicturePreview(json.profilePictureUrl);
                UI.pushInfoNotification('Profile picture successfully updated!', UI_NOTIFICATION_TIMEOUT);
            });

            fetch(PROFILE_PICTURE_UPLOAD_URL, {
                method: 'POST',
                headers: requestHeaders,
                body: formData
            })
                .then(response => handler.handle(response))
                .catch(error => handler.handleCatchError(error));
        }, this.#profilePictureMimeType, PROFILE_PICTURE_JPEG_QUALITY);
    }

    #fetchEmailAddressSettings() {
        getAndHandle(EMAIL_SETTINGS_URL, json => {
            let email = json.email;
            let isValid = json.isValid;

            this.#emailAddressField.value = email;
            let elementId = 'email-validity-unknown';
            if (isValid === true) {
                elementId = 'email-valid';
            } else if (isValid === false) {
                elementId = 'email-invalid';
            }

            document.getElementById(elementId).classList.remove('hidden');
        });
    }

}

window.onload = () => new UserSettingsPage();
