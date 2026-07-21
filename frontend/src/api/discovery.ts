import { apiRequest } from './http'

export interface ShopType { id: number; name: string; icon: string }
export interface Shop {
  id: number; name: string; images?: string; image?: string; score?: number; comments?: number
  area?: string; distance?: number; avgPrice?: number; address?: string; openHours?: string
}
export interface Voucher {
  id: number; title: string; subTitle?: string; payValue: number; actualValue: number; type?: number
  stock?: number; beginTime?: string; endTime?: string
}
export interface User { id: number; nickName?: string; icon?: string }
export interface UserInfo { introduce?: string; gender?: string; city?: string; birthday?: string }
export interface Blog {
  id: number; userId: number; shopId?: number; title?: string; content?: string; images?: string
  name?: string; icon?: string; liked?: number; comments?: number; isLike?: boolean; createTime?: string
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  return search.toString() ? `?${search.toString()}` : ''
}

export const discoveryApi = {
  types: () => apiRequest<ShopType[]>('/shop-type/list'),
  hotBlogs: (current = 1) => apiRequest<Blog[]>(`/blog/hot${query({ current })}`),
  blog: (id: number) => apiRequest<Blog>(`/blog/${id}`),
  likeBlog: (id: number) => apiRequest<void>(`/blog/like/${id}`, { method: 'PUT' }),
  shops: (params: { typeId: number; current: number; x?: number; y?: number }) =>
    apiRequest<Shop[]>(`/shop/of/type${query(params)}`),
  shopsByName: (name: string) => apiRequest<Shop[]>(`/shop/of/name${query({ name, current: 1 })}`),
  shop: (id: number) => apiRequest<Shop>(`/shop/${id}`),
  vouchers: (shopId: number) => apiRequest<Voucher[]>(`/voucher/list/${shopId}`),
  buyVoucher: (id: number) => apiRequest<number>(`/voucher-order/seckill/${id}`, { method: 'POST' }),
  login: (payload: { phone: string; code?: string; password?: string }) =>
    apiRequest<string>('/user/login', { method: 'POST', body: JSON.stringify(payload) }),
  sendCode: (phone: string) => apiRequest<void>(`/user/code${query({ phone })}`, { method: 'POST' }),
  logout: () => apiRequest<void>('/user/logout', { method: 'POST' }),
  me: () => apiRequest<User>('/user/me'),
  user: (id: number) => apiRequest<User | null>(`/user/${id}`),
  userInfo: (id: number) => apiRequest<UserInfo | null>(`/user/info/${id}`),
  myBlogs: () => apiRequest<Blog[]>('/blog/of/me?current=1'),
  userBlogs: (id: number) => apiRequest<Blog[]>(`/blog/of/user${query({ id, current: 1 })}`),
  followBlogs: (lastId = Date.now(), offset = 0) =>
    apiRequest<{ list?: Blog[]; minTime?: number; offset?: number }>('/blog/of/follow' + query({ lastId, offset })),
  likes: (id: number) => apiRequest<User[]>(`/blog/likes/${id}`),
  isFollowing: (id: number) => apiRequest<boolean>(`/follow/or/not/${id}`),
  follow: (id: number, value: boolean) => apiRequest<void>(`/follow/${id}/${value}`, { method: 'PUT' }),
  commonFollows: (id: number) => apiRequest<User[]>(`/follow/common/${id}`),
  createBlog: (payload: { title: string; content: string; images: string; shopId: number }) =>
    apiRequest<number>('/blog', { method: 'POST', body: JSON.stringify(payload) }),
  uploadImage: (file: File) => {
    const body = new FormData()
    body.append('file', file)
    return apiRequest<string>('/upload/blog', { method: 'POST', body })
  },
  deleteImage: (name: string) => apiRequest<void>(`/upload/blog/delete${query({ name })}`),
}
